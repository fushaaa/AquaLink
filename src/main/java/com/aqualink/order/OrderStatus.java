package com.aqualink.order;
public enum OrderStatus {
    PENDING,
    POOLED,
    AWAITING_PROVIDER,
    PROVIDER_OFFERED,
    ASSIGNED,
    IN_TRANSIT,
    DELIVERED,
    CANCELLED,
    EXPIRED,
    FAILED
}