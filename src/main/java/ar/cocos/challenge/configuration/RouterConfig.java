package ar.cocos.challenge.configuration;

import ar.cocos.challenge.web.handler.InstrumentHandler;
import ar.cocos.challenge.web.handler.OrderHandler;
import ar.cocos.challenge.web.handler.PortfolioHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
public class RouterConfig {

    @Bean
    public RouterFunction<ServerResponse> routes(PortfolioHandler portfolioHandler, InstrumentHandler instrumentHandler, OrderHandler orderHandler) {
        return RouterFunctions.route()
                .GET(PortfolioHandler.URL_ENDPOINT, portfolioHandler::getByUserId)
                .GET(InstrumentHandler.URL_ENDPOINT, instrumentHandler::search)
                .POST(OrderHandler.URL_ENDPOINT, orderHandler::createOrder)
                .build();
    }

}
