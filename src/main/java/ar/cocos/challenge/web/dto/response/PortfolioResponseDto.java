package ar.cocos.challenge.web.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class PortfolioResponseDto {

    private Integer userId;
    private String email;
    private String accountNumber;
    private BigDecimal buyingPower;
    private BigDecimal totalValue;
    @Builder.Default
    private List<StockPositionResponseDto> positions = new ArrayList<>();

    public void addPosition(StockPositionResponseDto position) {
        this.positions.add(position);
    }

}
