package com.aqualink.order;

import com.aqualink.household.Household;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Entity
@Table(name = "pooled_order_members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PooledOrderMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pooled_order_id", nullable = false)
    private WaterOrder pooledOrder;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_request_id", nullable = false, unique = true)
    private WaterOrder sourceRequest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "household_id", nullable = false)
    private Household household;

    @Column(name = "quantity_l", nullable = false, updatable = false)
    private int quantityL;

    @Column(name = "unit_price", precision = 10, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "estimated_cost", precision = 12, scale = 2)
    private BigDecimal estimatedCost;

    @Column(name = "delivered_quantity_l")
    private Integer deliveredQuantityL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberStatus status = MemberStatus.COMMITTED;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt = Instant.now();

    PooledOrderMember(WaterOrder pool, WaterOrder request) {
        this.pooledOrder = pool;
        this.sourceRequest = request;
        this.household = request.getHousehold();
        this.quantityL = request.getQuantityL();
    }

    void applyPrice(BigDecimal pricePerLitre) {
        this.unitPrice = pricePerLitre;
        this.estimatedCost = pricePerLitre
                .multiply(BigDecimal.valueOf(quantityL))
                .setScale(2, RoundingMode.HALF_UP);
    }
}