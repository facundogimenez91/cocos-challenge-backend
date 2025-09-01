package ar.cocos.challenge.core.service;

import ar.cocos.challenge.core.domain.User;
import reactor.core.publisher.Mono;

public interface UserService {

    Mono<User> get(Integer userId);

}
