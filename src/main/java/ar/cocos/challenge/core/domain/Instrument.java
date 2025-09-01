package ar.cocos.challenge.core.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("instruments")
public class Instrument {

    @Id
    private Integer id;
    @Column("ticker")
    private String ticker;
    @Column("name")
    private String name;
    @Column("type")
    private InstrumentType type;

}