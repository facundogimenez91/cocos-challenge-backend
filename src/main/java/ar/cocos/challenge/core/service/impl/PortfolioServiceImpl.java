package ar.cocos.challenge.core.service.impl;

import ar.cocos.challenge.configuration.PortfolioProperty;
import ar.cocos.challenge.core.domain.Instrument;
import ar.cocos.challenge.core.domain.MarketData;
import ar.cocos.challenge.core.domain.Order;
import ar.cocos.challenge.core.exception.UserNotFoundException;
import ar.cocos.challenge.core.repository.InstrumentRepository;
import ar.cocos.challenge.core.repository.MarketDataRepository;
import ar.cocos.challenge.core.repository.OrderRepository;
import ar.cocos.challenge.core.service.PortfolioService;
import ar.cocos.challenge.core.service.UserService;
import ar.cocos.challenge.web.dto.response.PortfolioResponseDto;
import ar.cocos.challenge.web.dto.response.StockPositionResponseDto;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

import static ar.cocos.challenge.core.domain.OrderSide.BUY;
import static ar.cocos.challenge.core.domain.OrderSide.SELL;
import static ar.cocos.challenge.core.domain.OrderStatus.FILLED;

/**
 * Service responsible for building and providing user portfolio information.
 * <p>
 * This service aggregates user account data, stock positions, cash positions (ARS),
 * and computes the total account value. It interacts with repositories to fetch
 * user, order, instrument, and market data, and handles all calculations and error
 * handling related to portfolio construction.
 * </p>
 */
@Service
@Log4j2
public class PortfolioServiceImpl implements PortfolioService {

    private final UserService userService;
    private final OrderRepository orderRepository;
    private final InstrumentRepository instrumentRepository;
    private final MarketDataRepository marketDataRepository;
    @Getter
    private final Boolean failOnDataCorruption;

    public PortfolioServiceImpl(UserService userService, OrderRepository orderRepository, InstrumentRepository instrumentRepository, MarketDataRepository marketDataRepository, PortfolioProperty portfolioProperty) {
        this.userService = userService;
        this.orderRepository = orderRepository;
        this.instrumentRepository = instrumentRepository;
        this.marketDataRepository = marketDataRepository;
        this.failOnDataCorruption = portfolioProperty.getFailOnDataCorruption();
    }

    /**
     * Builds the portfolio for the given user ID, including cash (ARS) and positions.
     *
     * @param userId the ID of the user
     * @return a Mono emitting the PortfolioDto containing user account data, positions, cash, and total value
     * @throws UserNotFoundException if the user is not found
     */
    @Override
    public Mono<PortfolioResponseDto> get(Integer userId) {
        return userService.get(userId)
                .map(userEntity -> PortfolioResponseDto.builder()
                        .accountNumber(userEntity.getAccountNumber())
                        .userId(userEntity.getId())
                        .email(userEntity.getEmail())
                        .build())
                .flatMap(this::buildPortfolio);
    }

    /**
     * Builds the detailed portfolio for a user by fetching and aggregating
     * stock positions and ARS cash position.
     *
     * @param portfolioResponseDto the initial PortfolioDto with user info
     * @return a Mono emitting the completed PortfolioDto with positions and values
     */
    private Mono<PortfolioResponseDto> buildPortfolio(PortfolioResponseDto portfolioResponseDto) {
        // We share the same stream of order-data for building stock and cash positions
        var fluxOrdersCache = orderRepository.findByUserIdAndStatus(portfolioResponseDto.getUserId(), FILLED).cache();
        return getUserStockPositions(portfolioResponseDto, fluxOrdersCache)
                .flatMap(e -> getUserPesosPosition(portfolioResponseDto, fluxOrdersCache));
    }

    /**
     * Aggregates and calculates all stock positions for the user, based on filled buy/sell orders.
     * Each position is added to the portfolio if the quantity is positive.
     *
     * @param portfolioResponseDto the PortfolioDto to update with stock positions
     * @param orderFlux            a Flux of all filled orders for the user
     * @return a Mono emitting the updated PortfolioDto with stock positions added
     */
    private Mono<PortfolioResponseDto> getUserStockPositions(PortfolioResponseDto portfolioResponseDto, Flux<Order> orderFlux) {
        return orderFlux
                // Only orders with BUY or SELL, we know that CASH_* are for cash
                .filter(o -> o.getSide() == BUY || o.getSide() == SELL)
                // Group orders by instrumentId to process positions per instrument
                .groupBy(Order::getInstrumentId)
                // Collecting orders, fetching instrument and MD
                .flatMap(group -> Mono.zip(
                        instrumentRepository.findById(group.key()),
                        marketDataRepository.findFirstByInstrumentIdOrderByDateDesc(group.key()).defaultIfEmpty(new MarketData()),
                        group.collectList()))
                .doOnNext(tuple -> {
                    var instrument = tuple.getT1();
                    var marketData = tuple.getT2();
                    var filledOrders = tuple.getT3();
                    var positionDtoOptional = buildStockPosition(instrument, marketData, filledOrders);
                    positionDtoOptional.ifPresent(portfolioResponseDto::addPosition);
                })
                .then(Mono.just(portfolioResponseDto));
    }

    /**
     * Calculates the user's ARS (cash) position based on all filled orders,
     * including cash in/out and netting stock trades. Updates the portfolio with
     * available cash (buying power) and total account value.
     *
     * @param portfolioResponseDto the PortfolioDto to update with cash and total value
     * @param orderFlux            a Flux of all filled orders for the user
     * @return a Mono emitting the updated PortfolioDto with cash and total value set
     */
    private Mono<PortfolioResponseDto> getUserPesosPosition(PortfolioResponseDto portfolioResponseDto, Flux<Order> orderFlux) {
        return orderFlux
                .collectList()
                .map(filledOrders -> {
                    var buyingPower = Order.getBuyingPower(filledOrders);
                    portfolioResponseDto.setBuyingPower(buyingPower);
                    var positionsValue = portfolioResponseDto.getPositions().stream()
                            .map(StockPositionResponseDto::getValue)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    var totalAccountValue = buyingPower.add(positionsValue);
                    portfolioResponseDto.setTotalValue(totalAccountValue);
                    return portfolioResponseDto;
                });
    }

    /**
     * Builds a stock position for a given instrument and its filled orders.
     * Calculates quantity, average buy price, position value, and P&L percent.
     * <p>
     * If the computed position quantity is negative:
     * <ul>
     *   <li>If {@code failOnDataCorruption} is {@code true}, an exception is thrown when negative positions are detected.</li>
     *   <li>If {@code failOnDataCorruption} is {@code false}, a warning is logged and the inconsistent position is skipped (not included in the portfolio).</li>
     * </ul>
     * If the computed position quantity is zero, the position is skipped.
     * </p>
     *
     * @param instrument   the InstrumentEntity for the stock
     * @param md           the latest MarketDataEntity for the stock (maybe empty)
     * @param filledOrders the list of all filled buy/sell orders for this instrument
     * @return Optional containing StockPositionDto if position is valid, or empty if zero/invalid
     */
    private Optional<StockPositionResponseDto> buildStockPosition(Instrument instrument, MarketData md, List<Order> filledOrders) {
        var buyQty = filledOrders.stream()
                .filter(o -> o.getSide() == BUY)
                .mapToInt(Order::getSize)
                .sum();
        var sellQty = filledOrders.stream()
                .filter(o -> o.getSide() == SELL)
                .mapToInt(Order::getSize)
                .sum();
        var quantity = buyQty - sellQty;
        if (quantity < 0) {
            if (Boolean.TRUE.equals(getFailOnDataCorruption())) {
                throw new IllegalStateException("Inconsistent trades detected for instrument " + instrument.getTicker() + " please check data source");
            } else {
                log.warn("Inconsistent trades for instrument {} — skipping position", instrument.getTicker());
                return Optional.empty();
            }
        }
        if (quantity == 0) {
            return Optional.empty();
        }
        var totalBuyCost = filledOrders.stream()
                .filter(o -> o.getSide() == BUY)
                .map(o -> o.getPrice().multiply(BigDecimal.valueOf(o.getSize())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        var avgPrice = (buyQty > 0)
                ? totalBuyCost.divide(BigDecimal.valueOf(buyQty), 8, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        var currentPrice = md.getClose() != null
                ? md.getClose()
                : BigDecimal.ZERO;
        var positionValue = currentPrice.multiply(BigDecimal.valueOf(quantity));
        var pnlPercent = (avgPrice.signum() > 0 && currentPrice.signum() > 0)
                ? currentPrice.divide(avgPrice, 8, RoundingMode.HALF_UP)
                .subtract(BigDecimal.ONE)
                .multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;
        return Optional.of(StockPositionResponseDto.builder()
                .quantity(quantity)
                .value(positionValue)
                .ticker(instrument.getTicker())
                .pnlPercent(pnlPercent)
                .build());
    }

}