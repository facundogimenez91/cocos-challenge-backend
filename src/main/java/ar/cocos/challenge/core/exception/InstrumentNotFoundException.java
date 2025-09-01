package ar.cocos.challenge.core.exception;

public class InstrumentNotFoundException extends NotFoundException {

    public InstrumentNotFoundException(String ticker) {
        super("Instrument with ticker " + ticker + " not found");
    }

}
