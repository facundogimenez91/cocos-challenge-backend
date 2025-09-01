package ar.cocos.challenge.core.repository;

import ar.cocos.challenge.core.domain.Order;
import ar.cocos.challenge.core.domain.OrderStatus;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface OrderRepository extends ReactiveCrudRepository<Order, Integer> {

    Flux<Order> findByUserIdAndStatus(Integer userId, OrderStatus status);

}