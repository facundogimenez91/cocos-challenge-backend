package ar.cocos.challenge.core.service.impl;

import ar.cocos.challenge.configuration.PortfolioProperty;
import ar.cocos.challenge.core.domain.*;
import ar.cocos.challenge.core.repository.InstrumentRepository;
import ar.cocos.challenge.core.repository.MarketDataRepository;
import ar.cocos.challenge.core.repository.OrderRepository;
import ar.cocos.challenge.core.service.UserService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static ar.cocos.challenge.core.domain.OrderSide.*;
import static ar.cocos.challenge.core.domain.OrderStatus.FILLED;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceImplTest {

    @Mock
    private UserService userService;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private InstrumentRepository instrumentRepository;
    @Mock
    private MarketDataRepository marketDataRepository;
    @Mock
    private PortfolioProperty portfolioProperty;

    @Spy
    @InjectMocks
    private PortfolioServiceImpl service;

    private User user1;

    @BeforeEach
    void setup() {
        user1 = new User();
        user1.setId(1);
        user1.setEmail("cocos@test.com");
        user1.setAccountNumber("2881");
    }

    @Test
    @DisplayName("getPortfolio: two positions and correct ARS cash (synthetic data)")
    void get_twoPositions_andCash_synthetic() {
        // User
        when(userService.get(1)).thenReturn(Mono.just(user1));

        // Orders (FILLED only, synthetic):
        // CASH_IN 500000 ; CASH_OUT 50000
        // APPLE: BUY 100@10, SELL 20@12 -> qty 80, avg 10, close 11 => value 880, pnl +10%
        // BYMA: BUY 200@5            -> qty 200, avg 5, close 4  => value 800, pnl -20%
        var orders = List.of(
                order(1, 9001, CASH_IN, 500_000, BigDecimal.valueOf(1.00), dt("2024-01-01T10:00:00")),
                order(2, 100, BUY, 100, BigDecimal.valueOf(10.00), dt("2024-01-01T10:05:00")),
                order(3, 100, SELL, 20, BigDecimal.valueOf(12.00), dt("2024-01-01T11:00:00")),
                order(4, 200, BUY, 200, BigDecimal.valueOf(5.00), dt("2024-01-01T12:00:00")),
                order(5, 9001, CASH_OUT, 50_000, BigDecimal.valueOf(1.00), dt("2024-01-01T13:00:00"))
        );
        when(orderRepository.findByUserIdAndStatus(1, FILLED)).thenReturn(Flux.fromIterable(orders));

        // Instruments
        when(instrumentRepository.findById(100)).thenReturn(Mono.just(instrument(100, "APPLE")));
        when(instrumentRepository.findById(200)).thenReturn(Mono.just(instrument(200, "BYMA")));

        // Market data
        when(marketDataRepository.findFirstByInstrumentIdOrderByDateDesc(100))
                .thenReturn(Mono.just(md(100, BigDecimal.valueOf(11.00))));
        when(marketDataRepository.findFirstByInstrumentIdOrderByDateDesc(200))
                .thenReturn(Mono.just(md(200, BigDecimal.valueOf(4.00))));

        // Verify
        StepVerifier.create(service.get(1))
                .assertNext(dto -> {
                    // cash = 500000 - 50000 - (100*10 + 200*5) + (20*12) = 448240
                    assertBd(dto.getBuyingPower(), bd("448240"));

                    var positions = dto.getPositions();
                    Assertions.assertEquals(2, positions.size());

                    var aaa = positions.stream().filter(p -> "APPLE".equals(p.getTicker())).findFirst().orElseThrow();
                    Assertions.assertEquals(80, aaa.getQuantity());
                    assertBd(aaa.getValue(), bd("880.00"));
                    Assertions.assertEquals(0, aaa.getPnlPercent().compareTo(bd("10")));

                    var bbb = positions.stream().filter(p -> "BYMA".equals(p.getTicker())).findFirst().orElseThrow();
                    Assertions.assertEquals(200, bbb.getQuantity());
                    assertBd(bbb.getValue(), bd("800.00"));
                    Assertions.assertEquals(0, bbb.getPnlPercent().compareTo(bd("-20")));

                    // total = 448240 + 880 + 800 = 449920
                    assertBd(dto.getTotalValue(), bd("449920.00"));
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("getPortfolio: missing market data -> keep position with value=0 and pnl=0 (synthetic)")
    void get_missingMarketData_valueZero_synthetic() {
        when(userService.get(1)).thenReturn(Mono.just(user1));

        var orders = List.of(
                order(1, 9001, CASH_IN, 100_000, BigDecimal.valueOf(1.0), dt("2024-02-01T09:00:00")),
                order(2, 300, BUY, 10, BigDecimal.valueOf(100.0), dt("2024-02-01T09:05:00"))
        );
        when(orderRepository.findByUserIdAndStatus(1, FILLED)).thenReturn(Flux.fromIterable(orders));

        when(instrumentRepository.findById(300)).thenReturn(Mono.just(instrument(300, "YPFD")));
        when(marketDataRepository.findFirstByInstrumentIdOrderByDateDesc(300)).thenReturn(Mono.empty()); // no MD

        StepVerifier.create(service.get(1))
                .assertNext(dto -> {
                    var pos = dto.getPositions();
                    Assertions.assertEquals(1, pos.size());
                    var ddd = pos.get(0);
                    Assertions.assertEquals("YPFD", ddd.getTicker());
                    Assertions.assertEquals(10, ddd.getQuantity());
                    assertBd(ddd.getValue(), BigDecimal.ZERO);
                    Assertions.assertEquals(0, ddd.getPnlPercent().compareTo(BigDecimal.ZERO));
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("getPortfolio: inconsistent SELL is normalized (excess ignored) and no negative position")
    void get_inconsistentSell_normalized() {
        when(userService.get(1)).thenReturn(Mono.just(user1));

        // BUY 10@50 ; SELL 15@55 -> allowed 10, excess 5 ignored; cash = -500 + 550 = +50
        var orders = List.of(
                order(1, 400, BUY, 10, BigDecimal.valueOf(50.0), dt("2024-03-01T10:00:00")),
                order(2, 400, SELL, 15, BigDecimal.valueOf(55.0), dt("2024-03-01T11:00:00"))
        );
        when(orderRepository.findByUserIdAndStatus(1, FILLED)).thenReturn(Flux.fromIterable(orders));

        when(instrumentRepository.findById(400)).thenReturn(Mono.just(instrument(400, "YPFD")));
        when(marketDataRepository.findFirstByInstrumentIdOrderByDateDesc(400))
                .thenReturn(Mono.just(md(400, BigDecimal.valueOf(60.0))));

        boolean failOn = Boolean.TRUE.equals(service.getFailOnDataCorruption());
        if (failOn) {
            StepVerifier.create(service.get(1))
                    .expectError(IllegalStateException.class)
                    .verify();
        } else {
            StepVerifier.create(service.get(1))
                    .assertNext(dto -> {
                        // Ensure it doesn't crash, positions can be empty
                        Assertions.assertTrue(dto.getPositions() == null || dto.getPositions().isEmpty());
                    })
                    .verifyComplete();
        }
    }

    @Test
    @DisplayName("getPortfolio: inconsistent SELL with failOnDataCorruption=true -> throws")
    void get_inconsistentSell_failOnTrue_throws() {
        when(userService.get(1)).thenReturn(Mono.just(user1));

        var orders = List.of(
                order(1, 500, BUY, 10, BigDecimal.valueOf(100.0), dt("2024-04-01T10:00:00")),
                order(2, 500, SELL, 15, BigDecimal.valueOf(110.0), dt("2024-04-01T11:00:00"))
        );
        when(orderRepository.findByUserIdAndStatus(1, FILLED)).thenReturn(Flux.fromIterable(orders));
        when(instrumentRepository.findById(500)).thenReturn(Mono.just(instrument(500, "YPFD")));
        when(marketDataRepository.findFirstByInstrumentIdOrderByDateDesc(500)).thenReturn(Mono.just(md(500, BigDecimal.valueOf(120.0))));

        doReturn(true).when(service).getFailOnDataCorruption();

        StepVerifier.create(service.get(1))
                .expectError(IllegalStateException.class)
                .verify();
    }

    @Test
    @DisplayName("getPortfolio: inconsistent SELL with failOnDataCorruption=false -> skip and continue")
    void get_inconsistentSell_failOnFalse_skips() {
        when(userService.get(1)).thenReturn(Mono.just(user1));

        var orders = List.of(
                order(1, 600, BUY, 5, BigDecimal.valueOf(200.0), dt("2024-05-01T10:00:00")),
                order(2, 600, SELL, 8, BigDecimal.valueOf(210.0), dt("2024-05-01T11:00:00"))
        );
        when(orderRepository.findByUserIdAndStatus(1, FILLED)).thenReturn(Flux.fromIterable(orders));
        when(instrumentRepository.findById(600)).thenReturn(Mono.just(instrument(600, "APPLE")));
        when(marketDataRepository.findFirstByInstrumentIdOrderByDateDesc(600)).thenReturn(Mono.just(md(600, BigDecimal.valueOf(205.0))));

        doReturn(false).when(service).getFailOnDataCorruption();

        StepVerifier.create(service.get(1))
                .assertNext(dto -> {
                    // Inconsistent instrument should be skipped -> no positions
                    Assertions.assertTrue(dto.getPositions() == null || dto.getPositions().isEmpty());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("getPortfolio: inconsistent SELL with failOnDataCorruption=true -> throws (spy)")
    void get_inconsistentSell_failOnTrue_throws_spy() {
        when(userService.get(1)).thenReturn(Mono.just(user1));

        var orders = List.of(
                order(1, 700, BUY, 10, BigDecimal.valueOf(100.0), dt("2024-06-01T10:00:00")),
                order(2, 700, SELL, 15, BigDecimal.valueOf(110.0), dt("2024-06-01T11:00:00"))
        );
        when(orderRepository.findByUserIdAndStatus(1, FILLED)).thenReturn(Flux.fromIterable(orders));
        when(instrumentRepository.findById(700)).thenReturn(Mono.just(instrument(700, "YPFD")));
        when(marketDataRepository.findFirstByInstrumentIdOrderByDateDesc(700)).thenReturn(Mono.just(md(700, BigDecimal.valueOf(120.0))));

        // stub the getter on spy
        doReturn(true).when(service).getFailOnDataCorruption();

        StepVerifier.create(service.get(1))
                .expectError(IllegalStateException.class)
                .verify();
    }

    @Test
    @DisplayName("getPortfolio: inconsistent SELL with failOnDataCorruption=false -> skip and continue (spy)")
    void get_inconsistentSell_failOnFalse_skips_spy() {
        when(userService.get(1)).thenReturn(Mono.just(user1));

        var orders = List.of(
                order(1, 800, BUY, 5, BigDecimal.valueOf(200.0), dt("2024-07-01T10:00:00")),
                order(2, 800, SELL, 8, BigDecimal.valueOf(210.0), dt("2024-07-01T11:00:00"))
        );
        when(orderRepository.findByUserIdAndStatus(1, FILLED)).thenReturn(Flux.fromIterable(orders));
        when(instrumentRepository.findById(800)).thenReturn(Mono.just(instrument(800, "APPLE")));
        when(marketDataRepository.findFirstByInstrumentIdOrderByDateDesc(800)).thenReturn(Mono.just(md(800, BigDecimal.valueOf(205.0))));

        // stub the getter on spy
        doReturn(false).when(service).getFailOnDataCorruption();

        StepVerifier.create(service.get(1))
                .assertNext(dto -> {
                    // Inconsistent instrument should be skipped -> no positions
                    Assertions.assertTrue(dto.getPositions() == null || dto.getPositions().isEmpty());
                })
                .verifyComplete();
    }

    // ===== Helpers =====

    private static LocalDateTime dt(String iso) {
        return LocalDateTime.parse(iso);
    }

    private static Order order(int id,
                               int instrumentId,
                               OrderSide side,
                               int size,
                               BigDecimal price,
                               LocalDateTime dt) {
        var o = new Order();
        o.setId(id);
        o.setInstrumentId(instrumentId);
        o.setUserId(1);
        o.setSide(side);
        o.setSize(size);
        o.setPrice(price);
        o.setType(OrderType.MARKET);
        o.setStatus(FILLED);
        o.setDatetime(dt);
        return o;
    }

    private static Instrument instrument(int id, String ticker) {
        var i = new Instrument();
        i.setId(id);
        i.setTicker(ticker);
        i.setName(ticker);
        return i;
    }

    private static MarketData md(int instrumentId, BigDecimal close) {
        var m = new MarketData();
        m.setInstrumentId(instrumentId);
        m.setClose(close);
        return m;
    }

    private static void assertBd(BigDecimal actual, BigDecimal expected) {
        Assertions.assertEquals(
                0, actual.compareTo(expected),
                () -> "Expected=" + expected + ", actual=" + actual
        );
    }

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

}