package com.aqualink.order;

import java.time.Instant;

public record WaterOrderResponse(
    String publicId,
    OrderType orderType,
    OrderStatus status,
    int quantityL,
    Instant windowStart,
    Instant windowEnd
) {
    public static WaterOrderResponse from(WaterOrder order) {
        return new WaterOrderResponse(
            order.getPublicId(),
            order.getOrderType(),
            order.getStatus(),
            order.getQuantityL(),
            order.getWindowStart(),
            order.getWindowEnd()
        );
    }
}