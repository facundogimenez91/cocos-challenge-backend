package ar.cocos.challenge.core.repository;

import ar.cocos.challenge.core.domain.Instrument;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface InstrumentRepository extends ReactiveCrudRepository<Instrument, Integer> {

    @Query("""
              SELECT *
              FROM instruments
              WHERE ticker ILIKE CONCAT('%', :q, '%')
                 OR name   ILIKE CONCAT('%', :q, '%')
              ORDER BY ticker
              LIMIT :limit
            """)
    Flux<Instrument> searchPartial(String q, long limit);

    Mono<Instrument> getInstrumentByTicker(String ticker);

}