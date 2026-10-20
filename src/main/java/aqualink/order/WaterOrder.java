package com.aqualink.order;

import com.aqualink.community.Community;
import com.aqualink.household.Household;
import com.aqualink.provider.WaterProvider;
import com.aqualink.servicepoint.ServicePoint;
import com.aqualink.user.User;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "water_orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WaterOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true,
            updatable = false, length = 36)
    private String publicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, updatable = false)
    private OrderType orderType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "community_id", nullable = false)
    private Community community;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_point_id", nullable = false)
    private ServicePoint servicePoint;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "household_id")
    private Household household;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_user_id")
    private User requestedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id")
    private WaterProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Arrangement arrangement;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(name = "quantity_l", nullable = false)
    private int quantityL;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "window_start", nullable = false)
    private Instant windowStart;

    @Column(name = "window_end", nullable = false)
    private Instant windowEnd;

    @Column(name = "pool_by", nullable = false)
    private Instant poolBy;

    @Column(nullable = false)
    private short priority;

    @Column(name = "offer_expires_at")
    private Instant offerExpiresAt;

    @Column(name = "unit_price", precision = 10, scale = 4)
    private BigDecimal unitPrice;

    @Column(length = 3)
    private String currency;

    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    @Column(length = 255)
    private String notes;

    @Version
    private int version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    @OneToMany(
        mappedBy = "pooledOrder",
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    private List<PooledOrderMember> members = new ArrayList<>();

    @PrePersist
    void onCreate() {
        if (publicId == null) {
            publicId = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public static WaterOrder individual(
            Household household,
            User requester,
            int quantityL,
            Instant windowStart,
            Instant windowEnd,
            Instant poolBy,
            String idempotencyKey,
            String notes) {

        if (quantityL <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (!windowEnd.isAfter(windowStart)) {
            throw new IllegalArgumentException(
                "Delivery window end must follow its start");
        }

        WaterOrder order = new WaterOrder();
        order.publicId = UUID.randomUUID().toString();
        order.orderType = OrderType.INDIVIDUAL;
        order.community = household.getCommunity();
        order.servicePoint = household.getServicePoint();
        order.household = household;
        order.requestedBy = requester;
        order.arrangement = household.getServicePoint().getArrangement();
        order.status = OrderStatus.PENDING;
        order.quantityL = quantityL;
        order.latitude = household.getLatitude();
        order.longitude = household.getLongitude();
        order.windowStart = windowStart;
        order.windowEnd = windowEnd;
        order.poolBy = poolBy;
        order.priority = (short) (household.isVulnerable() ? 1 : 0);
        order.idempotencyKey = idempotencyKey;
        order.notes = notes;
        return order;
    }

    public static WaterOrder newPool(WaterOrder anchor) {
        WaterOrder pool = new WaterOrder();
        pool.publicId = UUID.randomUUID().toString();
        pool.orderType = OrderType.POOLED;
        pool.community = anchor.community;
        pool.servicePoint = anchor.servicePoint;
        pool.arrangement = anchor.arrangement;
        pool.status = OrderStatus.AWAITING_PROVIDER;
        pool.quantityL = 0;
        pool.latitude = anchor.latitude;
        pool.longitude = anchor.longitude;
        pool.windowStart = anchor.windowStart;
        pool.windowEnd = anchor.windowEnd;
        pool.poolBy = anchor.poolBy;
        pool.priority = anchor.priority;
        return pool;
    }

    public void addMember(WaterOrder request) {
        if (orderType != OrderType.POOLED) {
            throw new IllegalStateException("Target order is not a pool");
        }
        if (request.orderType != OrderType.INDIVIDUAL
                || request.status != OrderStatus.PENDING) {
            throw new IllegalStateException("Request is not poolable");
        }

        Instant nextStart = windowStart.isAfter(request.windowStart)
                ? windowStart : request.windowStart;
        Instant nextEnd = windowEnd.isBefore(request.windowEnd)
                ? windowEnd : request.windowEnd;

        if (!nextEnd.isAfter(nextStart)) {
            throw new IllegalArgumentException(
                "Delivery windows do not overlap");
        }

        PooledOrderMember member = new PooledOrderMember(this, request);
        members.add(member);

        quantityL = Math.addExact(quantityL, request.quantityL);
        windowStart = nextStart;
        windowEnd = nextEnd;
        poolBy = poolBy.isBefore(request.poolBy) ? poolBy : request.poolBy;
        priority = (short) Math.max(priority, request.priority);

        request.status = OrderStatus.POOLED;
    }

    public void offerTo(WaterProvider selected, Instant expiresAt) {
        if (orderType != OrderType.POOLED
                || status != OrderStatus.AWAITING_PROVIDER) {
            throw new IllegalStateException("Pool cannot be offered");
        }
        if (selected.getTripCapacityL() < quantityL) {
            throw new IllegalArgumentException(
                "Provider capacity is below pooled demand");
        }

        provider = selected;
        status = OrderStatus.PROVIDER_OFFERED;
        offerExpiresAt = expiresAt;
        unitPrice = selected.getPricePerLitre();
        currency = selected.getCurrency();

        members.forEach(member -> member.applyPrice(unitPrice));
    }
}