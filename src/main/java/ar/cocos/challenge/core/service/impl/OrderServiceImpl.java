package ar.cocos.challenge.core.service.impl;

import ar.cocos.challenge.core.domain.*;
import ar.cocos.challenge.core.repository.OrderRepository;
import ar.cocos.challenge.core.service.InstrumentService;
import ar.cocos.challenge.core.service.MarketDataService;
import ar.cocos.challenge.core.service.OrderService;
import ar.cocos.challenge.core.service.UserService;
import ar.cocos.challenge.web.dto.request.OrderCreateRequestDto;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static ar.cocos.challenge.core.domain.OrderSide.BUY;
import static ar.cocos.challenge.core.domain.OrderSide.SELL;
import static ar.cocos.challenge.core.domain.OrderStatus.*;
import static ar.cocos.challenge.core.domain.OrderType.MARKET;
import static java.math.RoundingMode.DOWN;
import static java.math.RoundingMode.HALF_UP;

/**
 * Implementation of the {@link OrderService} interface that handles creation and validation of trading orders.
 *
 * <p>This service is responsible for:
 * <ul>
 *   <li>Validating the incoming order request fields and business rules.</li>
 *   <li>Fetching user and instrument details for the order context.</li>
 *   <li>Fetching market data when required for MARKET orders.</li>
 *   <li>Calculating order size from amount or size parameters.</li>
 *   <li>Validating buying power for BUY orders and holdings for SELL orders.</li>
 *   <li>Persisting the order with appropriate status (FILLED, NEW, or REJECTED).</li>
 * </ul>
 * </p>
 *
 * <p>All calculations use {@link BigDecimal} for financial precision and rounding behavior follows
 * explicit rules (HALF_UP for prices, DOWN for size calculations from amount).</p>
 */
@Service
@Log4j2
public class OrderServiceImpl implements OrderService {

    private final InstrumentService instrumentService;
    private final UserService userService;
    private final MarketDataService marketDataService;
    private final OrderRepository orderRepository;

    public OrderServiceImpl(InstrumentService instrumentService, UserService userService, MarketDataService marketDataService, OrderRepository orderRepository) {
        this.instrumentService = instrumentService;
        this.userService = userService;
        this.marketDataService = marketDataService;
        this.orderRepository = orderRepository;
    }

    /**
     * Creates a new order following the challenge rules.
     * <p>Pipeline:</p>
     * <ol>
     *   <li>Validate request-level business rules (size/amount, MARKET/LIMIT price constraints).</li>
     *   <li>Load user and instrument; for MARKET orders also load last market data (close).</li>
     *   <li>Compute execution price and order size (from amount or size, no fractional shares).</li>
     *   <li>Compute buying power and validate funds (BUY) or holdings (SELL).</li>
     *   <li>Persist the order with status: {@code FILLED} (MARKET), {@code NEW} (LIMIT), or {@code REJECTED}.</li>
     * </ol>
     *
     * @param orderCreateRequestDto incoming request DTO.
     * @return a {@link Mono} that emits the persisted {@link Order}.
     */
    @Override
    public Mono<Order> create(OrderCreateRequestDto orderCreateRequestDto) {
        validateRequest(orderCreateRequestDto);
        var context = new OrderContextHolder();
        context.setRequest(orderCreateRequestDto);
        return setUser(context)
                .flatMap(this::setAndGetInstrument)
                .flatMap(this::setAndGetMarketData)
                .flatMap(this::setAndGetBuyingPower)
                .map(this::setOrder)
                .flatMap(this::validateFundsAndHoldings)
                .flatMap(this::saveOrder)
                .map(OrderContextHolder::getOrder);
    }

    /**
     * Loads the user into the context. Errors from {@link UserService#get(Integer)} are propagated.
     */
    private Mono<OrderContextHolder> setUser(OrderContextHolder context) {
        return userService.get(context.getRequest().getUserId())
                .map(user -> {
                    context.setUser(user);
                    return context;
                });
    }

    /**
     * Loads the instrument into the context. Errors from {@link InstrumentService#get(String)} are propagated.
     */
    private Mono<OrderContextHolder> setAndGetInstrument(OrderContextHolder context) {
        return instrumentService.get(context.getRequest().getInstrumentTicker())
                .map(instrument -> {
                    context.setInstrument(instrument);
                    return context;
                });
    }

    /**
     * For MARKET orders only, loads the latest market data (close) into the context; otherwise returns the context unchanged.
     */
    private Mono<OrderContextHolder> setAndGetMarketData(OrderContextHolder context) {
        if (context.getRequest().getType().equals(MARKET)) {
            return marketDataService.get(context.getInstrument().getId())
                    .flatMap(marketData -> {
                        context.setMarketData(marketData);
                        return Mono.just(context);
                    });
        }
        return Mono.just(context);
    }

    /**
     * Builds the {@link Order} from the gathered context, computes execution price, derives size from amount when present
     * using {@code RoundingMode.DOWN}, applies scale(2) to price, and sets initial status (FILLED for MARKET, NEW for LIMIT).
     */
    private OrderContextHolder setOrder(OrderContextHolder context) {
        var request = context.getRequest();
        var execPrice = request.getType() == MARKET ? (context.getMarketData().getClose()) : request.getPrice();
        var order = new Order();
        order.setUserId(context.getUser().getId());
        order.setInstrumentId(context.getInstrument().getId());
        order.setSide(request.getSide());
        order.setType(request.getType());
        order.setDatetime(LocalDateTime.now());
        order.setPrice(execPrice.setScale(2, HALF_UP));
        Integer computedSize;
        if (request.getAmount() != null) {
            var sizeFromAmount = request.getAmount().divide(execPrice, 0, DOWN).intValue();
            if (sizeFromAmount <= 0) {
                throw new IllegalArgumentException("amount too small to execute at current price");
            }
            computedSize = sizeFromAmount;
        } else {
            computedSize = request.getSize();
        }
        order.setSize(computedSize);
        order.setStatus(request.getType() == MARKET ? FILLED : NEW);
        context.setOrder(order);
        return context;
    }

    /**
     * Aggregates user's FILLED orders to compute buying power and stores it in the context.
     */
    private Mono<OrderContextHolder> setAndGetBuyingPower(OrderContextHolder orderContextHolder) {
        return orderRepository.findByUserIdAndStatus(orderContextHolder.getUser().getId(), FILLED)
                .collectList()
                .map(filled -> {
                    orderContextHolder.setBuyingPower(Order.getBuyingPower(filled));
                    return orderContextHolder;
                });
    }

    /**
     * Validates business constraints prior to persistence.
     * <ul>
     *   <li>BUY: checks that buying power >= price * size; otherwise persists REJECTED.</li>
     *   <li>SELL: checks that net holdings (FILLED BUY - SELL) for the instrument >= size; otherwise persists REJECTED.</li>
     * </ul>
     * Returns the same context, possibly after persisting a REJECTED order.
     */
    private Mono<OrderContextHolder> validateFundsAndHoldings(OrderContextHolder context) {
        var order = context.getOrder();
        if (order.getSide() == BUY) {
            var required = order.getPrice().multiply(BigDecimal.valueOf(order.getSize()));
            if (context.getBuyingPower().compareTo(required) < 0) {
                order.setStatus(REJECTED);
                log.warn("Order REJECTED: insufficient cash. needed={}, available={}", required, context.getBuyingPower());
                return orderRepository.save(order).thenReturn(context);
            }
            return Mono.just(context);
        }
        if (order.getSide() == SELL) {
            return orderRepository.findByUserIdAndStatus(context.getUser().getId(), FILLED)
                    .filter(ord -> ord.getInstrumentId().equals(order.getInstrumentId()))
                    .collectList()
                    .flatMap(filled -> {
                        var netQty = filled.stream()
                                .map(ord -> ord.getSide() == BUY ? ord.getSize() : -ord.getSize())
                                .reduce(0, Integer::sum);
                        if (netQty < order.getSize()) {
                            order.setStatus(REJECTED);
                            log.warn("Order REJECTED: insufficient holdings. have={}, tryingToSell={}", netQty, order.getSize());
                            return orderRepository.save(order).thenReturn(context);
                        }
                        return Mono.just(context);
                    });
        }
        return Mono.just(context);
    }

    /**
     * Persists the built order and returns the same context.
     */
    private Mono<OrderContextHolder> saveOrder(OrderContextHolder context) {
        var order = context.getOrder();
        return orderRepository.save(order).thenReturn(context);
    }

    /**
     * Request-level validation that does not require DB access.
     * <ul>
     *   <li>Rejects CASH_IN/CASH_OUT for this endpoint.</li>
     *   <li>MARKET: price must be null.</li>
     *   <li>LIMIT: price > 0.</li>
     *   <li>BUY/SELL: exactly one of size (>0) or amount (>0) must be provided.</li>
     * </ul>
     */
    private void validateRequest(OrderCreateRequestDto r) {
        if (r.getInstrumentTicker() == null || r.getInstrumentTicker().isBlank()) {
            throw new IllegalArgumentException("instrument ticker is null or blank");
        }
        if (r.getUserId() == null) {
            throw new IllegalArgumentException("user id is null");
        }
        if (r.getType() == null) {
            throw new IllegalArgumentException("type is null");
        }
        if (r.getSide() == null) {
            throw new IllegalArgumentException("side is null");
        }
        if (r.getSide() == OrderSide.CASH_IN || r.getSide() == OrderSide.CASH_OUT) {
            throw new IllegalArgumentException("CASH_IN/CASH_OUT are not supported by this endpoint");
        }
        if (r.getType() == MARKET && r.getPrice() != null) {
            throw new IllegalArgumentException("price must be omitted for MARKET");
        }
        if (r.getType() == OrderType.LIMIT && (r.getPrice() == null || r.getPrice().signum() <= 0)) {
            throw new IllegalArgumentException("price must be > 0 for LIMIT");
        }
        boolean hasSize = r.getSize() != null && r.getSize() > 0;
        boolean hasAmount = r.getAmount() != null && r.getAmount().signum() > 0;
        if (hasSize == hasAmount) {
            throw new IllegalArgumentException("provide exactly one of size or amount");
        }
    }

    /**
     * Lightweight mutable context passed along the reactive pipeline to avoid wide method signatures.
     */
    @Data
    private static class OrderContextHolder {
        private Order order;
        private BigDecimal buyingPower;
        private User user;
        private Instrument instrument;
        private MarketData marketData;
        private OrderCreateRequestDto request;
    }

}
