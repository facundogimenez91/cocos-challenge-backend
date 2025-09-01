package ar.cocos.challenge.core.exception;

public class UserNotFoundException extends NotFoundException {

    public UserNotFoundException(Integer userId) {
        super("User with id " + userId + " not found");
    }

}
