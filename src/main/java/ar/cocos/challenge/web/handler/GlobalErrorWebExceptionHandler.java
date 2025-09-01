package ar.cocos.challenge.web.handler;

import ar.cocos.challenge.core.exception.NotFoundException;
import ar.cocos.challenge.web.dto.response.Error;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.autoconfigure.web.reactive.error.AbstractErrorWebExceptionHandler;
import org.springframework.boot.web.reactive.error.ErrorAttributes;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.stereotype.Component;
import org.springframework.web.ErrorResponse;
import org.springframework.web.reactive.function.server.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;


@Component
@Order(-2)
@Log4j2
public class GlobalErrorWebExceptionHandler extends AbstractErrorWebExceptionHandler {

    public GlobalErrorWebExceptionHandler(ErrorAttributes errorAttributes, WebProperties webProperties, ApplicationContext applicationContext, ServerCodecConfigurer serverCodecConfigurer) {
        super(errorAttributes, webProperties.getResources(), applicationContext);
        setMessageWriters(serverCodecConfigurer.getWriters());
    }

    @Override
    protected RouterFunction<ServerResponse> getRoutingFunction(ErrorAttributes errorAttributes) {
        return RouterFunctions.route(RequestPredicates.all(), this::renderErrorResponse);
    }

    private Mono<ServerResponse> renderErrorResponse(ServerRequest serverRequest) {
        var throwable = getError(serverRequest);
        var errorResponse = Error.builder().build();
        if (throwable instanceof ResponseStatusException responseStatusException) {
            errorResponse = Error.builder()
                    .status(responseStatusException.getStatusCode())
                    .message(responseStatusException.getMessage())
                    .build();
        }
        if (throwable instanceof NotFoundException notFoundException) {
            errorResponse = Error.builder()
                    .status(HttpStatusCode.valueOf(HttpStatus.NOT_FOUND.value()))
                    .message(notFoundException.getMessage())
                    .build();
        }
        if (throwable instanceof IllegalArgumentException illegalArgumentException) {
            errorResponse = Error.builder()
                    .status(HttpStatusCode.valueOf(HttpStatus.BAD_REQUEST.value()))
                    .message(illegalArgumentException.getMessage())
                    .build();
        }
        return ServerResponse
                .status(errorResponse.getStatus())
                .body(Mono.just(errorResponse), ErrorResponse.class);
    }

}
