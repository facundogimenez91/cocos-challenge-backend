package ar.cocos.challenge.core.service;

import ar.cocos.challenge.web.dto.response.PortfolioResponseDto;
import reactor.core.publisher.Mono;

public interface PortfolioService {

    Mono<PortfolioResponseDto> get(Integer userId);

}
