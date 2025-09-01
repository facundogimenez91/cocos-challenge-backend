package ar.cocos.challenge.core.repository;

import ar.cocos.challenge.core.domain.MarketData;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface MarketDataRepository extends ReactiveCrudRepository<MarketData, Integer> {

    Mono<MarketData> findFirstByInstrumentIdOrderByDateDesc(Integer instrumentId);

}