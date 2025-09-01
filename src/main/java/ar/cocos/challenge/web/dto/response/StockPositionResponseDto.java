package ar.cocos.challenge.web.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;


@Data
@Builder
public class StockPositionResponseDto {

    private String ticker;
    private Integer quantity;
    private BigDecimal value;
    private BigDecimal pnlPercent;

}
