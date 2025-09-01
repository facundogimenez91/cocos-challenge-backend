package ar.cocos.challenge.core.service.impl;

import ar.cocos.challenge.core.domain.MarketData;
import ar.cocos.challenge.core.exception.MarketDataNotFoundException;
import ar.cocos.challenge.core.repository.MarketDataRepository;
import ar.cocos.challenge.core.service.MarketDataService;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Implementation of {@link MarketDataService} that provides access to the latest market data for instruments.
 *
 * <p>This service fetches the most recent market data entry for a given instrument ID. If no market data
 * is found, a {@link ar.cocos.challenge.core.exception.MarketDataNotFoundException} is thrown.</p>
 */
@Service
@Log4j2
public class MarketServiceImpl implements MarketDataService {

    private final MarketDataRepository marketDataRepository;

    public MarketServiceImpl(MarketDataRepository marketDataRepository) {
        this.marketDataRepository = marketDataRepository;
    }

    /**
     * Retrieves the most recent {@link MarketData} for the given instrument ID.
     *
     * @param id the instrument ID.
     * @return a {@link Mono} emitting the latest {@link MarketData} for the instrument.
     * Emits a {@link ar.cocos.challenge.core.exception.MarketDataNotFoundException} if no data is found.
     */
    @Override
    public Mono<MarketData> get(Integer id) {
        return marketDataRepository.findFirstByInstrumentIdOrderByDateDesc(id)
                .switchIfEmpty(Mono.error(new MarketDataNotFoundException(id)));
    }

}
