package ar.cocos.challenge.core.service;

import ar.cocos.challenge.core.domain.Instrument;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface InstrumentService {

    Flux<Instrument> search(String query);

    Mono<Instrument> get(String ticket);

}
