package ar.cocos.challenge.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "instrument")
@Data
public class InstrumentProperty {

    private Long ttlMin;
    private Long maxSize;
    private Long limit;

}
