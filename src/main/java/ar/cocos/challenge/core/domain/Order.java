package ar.cocos.challenge.core.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static ar.cocos.challenge.core.domain.OrderSide.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("orders")
public class Order {

    @Id
    private Integer id;
    @Column("instrumentid")
    private Integer instrumentId;
    @Column("userid")
    private Integer userId;
    @Column("size")
    private Integer size;
    @Column("price")
    private BigDecimal price;
    @Column("type")
    private OrderType type;
    @Column("side")
    private OrderSide side;
    @Column("status")
    private OrderStatus status;
    @Column("datetime")
    private LocalDateTime datetime;

    /**
     * Calculates the buying power (available cash) for a user based on their filled orders.
     *
     * <p>The formula is:
     * <ul>
     *   <li><b>CASH_IN</b>: adds to available cash</li>
     *   <li><b>CASH_OUT</b>: subtracts from available cash</li>
     *   <li><b>BUY</b>: subtracts total value (price * size)</li>
     *   <li><b>SELL</b>: adds total value (price * size)</li>
     * </ul>
     *
     * @param filledOrders list of orders with status FILLED
     * @return a {@link BigDecimal} representing the user's available cash to place new BUY orders
     */
    public static BigDecimal getBuyingPower(List<Order> filledOrders) {
        var cashIn = filledOrders.stream()
                .filter(o -> o.getSide() == CASH_IN)
                .mapToLong(Order::getSize)
                .sum();
        var cashOut = filledOrders.stream()
                .filter(o -> o.getSide() == CASH_OUT)
                .mapToLong(Order::getSize)
                .sum();
        var totalBuys = filledOrders.stream()
                .filter(o -> o.getSide() == BUY)
                .map(o -> o.getPrice().multiply(BigDecimal.valueOf(o.getSize())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        var totalSells = filledOrders.stream()
                .filter(o -> o.getSide() == SELL)
                .map(o -> o.getPrice().multiply(BigDecimal.valueOf(o.getSize())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return BigDecimal.valueOf(cashIn)
                .subtract(BigDecimal.valueOf(cashOut))
                .subtract(totalBuys)
                .add(totalSells);
    }

}