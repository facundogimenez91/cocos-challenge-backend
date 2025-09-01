package ar.cocos.challenge.core.service.impl;

import ar.cocos.challenge.core.domain.*;
import ar.cocos.challenge.core.repository.OrderRepository;
import ar.cocos.challenge.core.service.InstrumentService;
import ar.cocos.challenge.core.service.MarketDataService;
import ar.cocos.challenge.core.service.UserService;
import ar.cocos.challenge.web.dto.request.OrderCreateRequestDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock InstrumentService instrumentService;
    @Mock UserService userService;
    @Mock MarketDataService marketDataService;
    @Mock OrderRepository orderRepository;

    @InjectMocks OrderServiceImpl service;

    @Captor ArgumentCaptor<Order> orderCaptor;

    private User user;
    private Instrument instrument;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1);
        user.setEmail("user@test.com");
        user.setAccountNumber("10001");

        instrument = new Instrument();
        instrument.setId(47);
        instrument.setTicker("PAMP");
        instrument.setName("Pampa Energía");
        instrument.setType(InstrumentType.ACCIONES); // texto libre, no condiciona en OrderServiceImpl actual
    }

    @Test
    void create_buyMarket_byAmount_filled_whenEnoughCash() {
        // GIVEN: MARKET, amount grande, close=100 → size=amount/price = 1500
        var req = new OrderCreateRequestDto();
        req.setInstrumentTicker("PAMP");
        req.setUserId(1);
        req.setType(OrderType.MARKET);
        req.setSide(OrderSide.BUY);
        req.setAmount(new BigDecimal("150000")); // 150k / 100 = 1500 shares

        var md = new MarketData();
        md.setInstrumentId(47);
        md.setClose(new BigDecimal("100.00"));
        md.setDate(LocalDate.now());

        // Buying power: simulamos CASH_IN previo de 1M
        var cashIn = new Order();
        cashIn.setUserId(1);
        cashIn.setInstrumentId(66); // cash
        cashIn.setSide(OrderSide.CASH_IN);
        cashIn.setStatus(OrderStatus.FILLED);
        cashIn.setPrice(new BigDecimal("1.00"));
        cashIn.setSize(1_000_000);

        when(userService.get(1)).thenReturn(Mono.just(user));
        when(instrumentService.get("PAMP")).thenReturn(Mono.just(instrument));
        when(marketDataService.get(47)).thenReturn(Mono.just(md));
        when(orderRepository.findByUserIdAndStatus(1, OrderStatus.FILLED)).thenReturn(Flux.fromIterable(List.of(cashIn)));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        // WHEN
        var result = service.create(req);

        // THEN
        StepVerifier.create(result)
                .assertNext(o -> {
                    assertEquals(OrderType.MARKET, o.getType());
                    assertEquals(OrderSide.BUY, o.getSide());
                    assertEquals(OrderStatus.FILLED, o.getStatus());
                    assertEquals(47, o.getInstrumentId());
                    assertEquals(1, o.getUserId());
                    assertEquals(1500, o.getSize());
                    assertEquals(new BigDecimal("100.00"), o.getPrice());
                })
                .verifyComplete();

        verify(orderRepository, atLeastOnce()).save(any(Order.class));
    }

    @Test
    void create_buyMarket_insufficientCash_rejected() {
        // GIVEN: MARKET BUY size=3, close=50 → required=150; buying power=100 → REJECTED
        var req = new OrderCreateRequestDto();
        req.setInstrumentTicker("PAMP");
        req.setUserId(1);
        req.setType(OrderType.MARKET);
        req.setSide(OrderSide.BUY);
        req.setSize(3);

        var md = new MarketData();
        md.setInstrumentId(47);
        md.setClose(new BigDecimal("50.00"));
        md.setDate(LocalDate.now());

        // Buying power: CASH_IN=100
        var cashIn = new Order();
        cashIn.setUserId(1);
        cashIn.setInstrumentId(66);
        cashIn.setSide(OrderSide.CASH_IN);
        cashIn.setStatus(OrderStatus.FILLED);
        cashIn.setPrice(new BigDecimal("1.00"));
        cashIn.setSize(100);

        when(userService.get(1)).thenReturn(Mono.just(user));
        when(instrumentService.get("PAMP")).thenReturn(Mono.just(instrument));
        when(marketDataService.get(47)).thenReturn(Mono.just(md));
        when(orderRepository.findByUserIdAndStatus(1, OrderStatus.FILLED)).thenReturn(Flux.fromIterable(List.of(cashIn)));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        var result = service.create(req);

        StepVerifier.create(result)
                .assertNext(o -> {
                    assertEquals(OrderStatus.REJECTED, o.getStatus());
                    assertEquals(3, o.getSize());
                    assertEquals(new BigDecimal("50.00"), o.getPrice());
                })
                .verifyComplete();

        verify(orderRepository, atLeastOnce()).save(orderCaptor.capture());
        var saved = orderCaptor.getValue();
        assertEquals(OrderStatus.REJECTED, saved.getStatus());
    }

    @Test
    void create_sellMarket_insufficientHoldings_rejected() {
        // GIVEN: MARKET SELL size=10, holdings netos = 5 → REJECTED
        var req = new OrderCreateRequestDto();
        req.setInstrumentTicker("PAMP");
        req.setUserId(1);
        req.setType(OrderType.MARKET);
        req.setSide(OrderSide.SELL);
        req.setSize(10);

        var md = new MarketData();
        md.setInstrumentId(47);
        md.setClose(new BigDecimal("120.00"));
        md.setDate(LocalDate.now());

        // Tenencia neta: 5 (BUY 5 en el mismo instrumento)
        var filledBuy = new Order();
        filledBuy.setUserId(1);
        filledBuy.setInstrumentId(47);
        filledBuy.setSide(OrderSide.BUY);
        filledBuy.setStatus(OrderStatus.FILLED);
        filledBuy.setPrice(new BigDecimal("100.00"));
        filledBuy.setSize(5);

        when(userService.get(1)).thenReturn(Mono.just(user));
        when(instrumentService.get("PAMP")).thenReturn(Mono.just(instrument));
        when(marketDataService.get(47)).thenReturn(Mono.just(md));
        when(orderRepository.findByUserIdAndStatus(1, OrderStatus.FILLED)).thenReturn(Flux.fromIterable(List.of(filledBuy)));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        var result = service.create(req);

        StepVerifier.create(result)
                .assertNext(o -> {
                    assertEquals(OrderStatus.REJECTED, o.getStatus());
                    assertEquals(10, o.getSize());
                    assertEquals(new BigDecimal("120.00"), o.getPrice());
                })
                .verifyComplete();

        verify(orderRepository, atLeastOnce()).save(any(Order.class));
    }

    @Test
    void create_buyLimit_new_whenEnoughCash() {
        // GIVEN: LIMIT BUY size=10, price=50 → required=500; buying power=1000 → NEW
        var req = new OrderCreateRequestDto();
        req.setInstrumentTicker("PAMP");
        req.setUserId(1);
        req.setType(OrderType.LIMIT);
        req.setSide(OrderSide.BUY);
        req.setSize(10);
        req.setPrice(new BigDecimal("50.00"));

        // Buying power: CASH_IN=1000
        var cashIn = new Order();
        cashIn.setUserId(1);
        cashIn.setInstrumentId(66);
        cashIn.setSide(OrderSide.CASH_IN);
        cashIn.setStatus(OrderStatus.FILLED);
        cashIn.setPrice(new BigDecimal("1.00"));
        cashIn.setSize(1000);

        when(userService.get(1)).thenReturn(Mono.just(user));
        when(instrumentService.get("PAMP")).thenReturn(Mono.just(instrument));
        // LIMIT no requiere market data
        when(orderRepository.findByUserIdAndStatus(1, OrderStatus.FILLED)).thenReturn(Flux.fromIterable(List.of(cashIn)));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        var result = service.create(req);

        StepVerifier.create(result)
                .assertNext(o -> {
                    assertEquals(OrderType.LIMIT, o.getType());
                    assertEquals(OrderStatus.NEW, o.getStatus());
                    assertEquals(10, o.getSize());
                    assertEquals(new BigDecimal("50.00"), o.getPrice());
                })
                .verifyComplete();

        verify(orderRepository, atLeastOnce()).save(any(Order.class));
    }

    @Test
    void create_buyMarket_amountTooSmall_throws() {
        // GIVEN: MARKET BUY amount < close → sizeFromAmount=0 → IllegalArgumentException (versión actual)
        var req = new OrderCreateRequestDto();
        req.setInstrumentTicker("PAMP");
        req.setUserId(1);
        req.setType(OrderType.MARKET);
        req.setSide(OrderSide.BUY);
        req.setAmount(new BigDecimal("90.00")); // close = 100, amount insuficiente

        var md = new MarketData();
        md.setInstrumentId(47);
        md.setClose(new BigDecimal("100.00"));
        md.setDate(LocalDate.now());

        // Buying power alto, pero no importa porque falla antes
        var cashIn = new Order();
        cashIn.setUserId(1);
        cashIn.setInstrumentId(66);
        cashIn.setSide(OrderSide.CASH_IN);
        cashIn.setStatus(OrderStatus.FILLED);
        cashIn.setPrice(new BigDecimal("1.00"));
        cashIn.setSize(1_000_000);

        when(userService.get(1)).thenReturn(Mono.just(user));
        when(instrumentService.get("PAMP")).thenReturn(Mono.just(instrument));
        when(marketDataService.get(47)).thenReturn(Mono.just(md));
        when(orderRepository.findByUserIdAndStatus(1, OrderStatus.FILLED)).thenReturn(Flux.fromIterable(List.of(cashIn)));

        var result = service.create(req);

        StepVerifier.create(result)
                .expectError(IllegalArgumentException.class)
                .verify();

        verify(orderRepository, never()).save(any(Order.class));
    }
}