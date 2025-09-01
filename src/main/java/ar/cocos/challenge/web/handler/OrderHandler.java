package ar.cocos.challenge.web.handler;

import ar.cocos.challenge.core.domain.Order;
import ar.cocos.challenge.core.service.OrderService;
import ar.cocos.challenge.web.dto.request.OrderCreateRequestDto;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
@AllArgsConstructor
public class OrderHandler {

    public static final String URL_ENDPOINT = "/challenge/v1/order";
    private final OrderService orderService;

    public Mono<ServerResponse> createOrder(ServerRequest serverRequest) {
        var responseMono = serverRequest.bodyToMono(OrderCreateRequestDto.class)
                .flatMap(orderService::create);
        return ServerResponse
                .ok()
                .body(responseMono, Order.class);
    }

}
