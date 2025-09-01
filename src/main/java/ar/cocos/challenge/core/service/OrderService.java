package ar.cocos.challenge.core.service;

import ar.cocos.challenge.core.domain.Order;
import ar.cocos.challenge.web.dto.request.OrderCreateRequestDto;
import reactor.core.publisher.Mono;

public interface OrderService {

    Mono<Order> create(OrderCreateRequestDto orderCreateRequestDto);

}
