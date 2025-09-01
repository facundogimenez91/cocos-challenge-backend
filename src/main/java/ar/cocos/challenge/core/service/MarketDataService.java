package ar.cocos.challenge.core.service;

import ar.cocos.challenge.core.domain.MarketData;
import reactor.core.publisher.Mono;

public interface MarketDataService {

    Mono<MarketData> get(Integer id);

}
