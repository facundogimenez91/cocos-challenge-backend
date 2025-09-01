package ar.cocos.challenge.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "portfolio")
@Data
public class PortfolioProperty {

    private Boolean failOnDataCorruption;

}
