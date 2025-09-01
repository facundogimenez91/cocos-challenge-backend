package ar.cocos.challenge.web.handler;

import ar.cocos.challenge.core.service.PortfolioService;
import ar.cocos.challenge.web.dto.response.PortfolioResponseDto;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
@AllArgsConstructor
public class PortfolioHandler {

    public static final String URL_ENDPOINT = "/challenge/v1/portfolio/user/{userId}";
    private final PortfolioService portfolioService;

    public Mono<ServerResponse> getByUserId(ServerRequest serverRequest) {
        var userId = serverRequest.pathVariable("userId");
        var response = portfolioService.get(Integer.valueOf(userId));
        return ServerResponse
                .ok()
                .body(response, PortfolioResponseDto.class);
    }

}
