package ar.cocos.challenge.web.dto.request;

import ar.cocos.challenge.core.domain.OrderSide;
import ar.cocos.challenge.core.domain.OrderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrderCreateRequestDto {

    private String instrumentTicker;
    private Integer userId;
    private OrderType type;
    private OrderSide side;
    private Integer size;
    private BigDecimal amount;
    private BigDecimal price;

}
