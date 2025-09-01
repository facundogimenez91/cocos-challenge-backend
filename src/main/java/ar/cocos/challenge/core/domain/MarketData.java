package ar.cocos.challenge.core.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table("marketdata")
public class MarketData {

    @Id
    private Integer id;
    @Column("instrumentid")
    private Integer instrumentId;
    @Column("high")
    private BigDecimal high;
    @Column("low")
    private BigDecimal low;
    @Column("open")
    private BigDecimal open;
    @Column("close")
    private BigDecimal close;
    @Column("previousclose")
    private BigDecimal previousClose;
    @Column("date")
    private LocalDate date;

}