package ar.cocos.challenge.core.service.impl;

import ar.cocos.challenge.core.domain.User;
import ar.cocos.challenge.core.exception.UserNotFoundException;
import ar.cocos.challenge.core.repository.UserRepository;
import ar.cocos.challenge.core.service.UserService;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Implementation of {@link UserService} that provides access to user information.
 *
 * <p>This service fetches user details by ID from the repository. If the user does not exist,
 * a {@link ar.cocos.challenge.core.exception.UserNotFoundException} is thrown.</p>
 */
@Service
@Log4j2
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Retrieves a user by ID.
     *
     * @param userId the ID of the user to retrieve.
     * @return a {@link Mono} emitting the {@link User} if found, otherwise emits {@link ar.cocos.challenge.core.exception.UserNotFoundException}.
     */
    @Override
    public Mono<User> get(Integer userId) {
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new UserNotFoundException(userId)));
    }

}
