package ar.cocos.challenge.web.handler;

import ar.cocos.challenge.core.domain.Instrument;
import ar.cocos.challenge.core.service.InstrumentService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
@AllArgsConstructor
public class InstrumentHandler {

    public static final String URL_ENDPOINT = "/challenge/v1/instrument/search/{id}";
    private final InstrumentService instrumentService;

    public Mono<ServerResponse> search(ServerRequest serverRequest) {
        var id = serverRequest.pathVariable("id");
        var response = instrumentService.search(id);
        return ServerResponse
                .ok()
                .body(response, Instrument.class);
    }

}
