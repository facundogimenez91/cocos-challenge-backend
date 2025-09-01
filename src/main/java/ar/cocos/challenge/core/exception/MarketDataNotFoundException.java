package ar.cocos.challenge.core.exception;

public class MarketDataNotFoundException extends NotFoundException {

    public MarketDataNotFoundException(Integer id) {
        super("No MD for instrument id " + id);
    }

}
