package com.aqualink.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface WaterOrderRepository
        extends JpaRepository<WaterOrder, Long> {

    Optional<WaterOrder> findByPublicId(String publicId);

    Optional<WaterOrder> findByHouseholdIdAndIdempotencyKey(
            Long householdId, String idempotencyKey);

    @Query("""
        select o from WaterOrder o
        where o.community.id = :communityId
          and o.orderType = :type
          and o.status = :status
        order by o.priority desc, o.poolBy asc, o.id asc
        """)
    Page<WaterOrder> findPoolCandidates(
            @Param("communityId") Long communityId,
            @Param("type") OrderType type,
            @Param("status") OrderStatus status,
            Pageable pageable);

    @Query("""
        select o from WaterOrder o
        where o.servicePoint.id = :servicePointId
          and o.orderType = :type
          and o.status = :status
          and o.windowStart < :windowEnd
          and o.windowEnd > :windowStart
        order by o.priority desc, o.createdAt asc, o.id asc
        """)
    Page<WaterOrder> findSharedPointCandidates(
            @Param("servicePointId") Long servicePointId,
            @Param("type") OrderType type,
            @Param("status") OrderStatus status,
            @Param("windowStart") java.time.Instant windowStart,
            @Param("windowEnd") java.time.Instant windowEnd,
            Pageable pageable);

    @EntityGraph(attributePaths = {"servicePoint", "community"})
    @Query("""
        select o from WaterOrder o
        where o.provider.id = :providerId
          and o.status in :statuses
        order by o.windowStart asc
        """)
    Page<WaterOrder> findActiveByProvider(
            @Param("providerId") Long providerId,
            @Param("statuses") Collection<OrderStatus> statuses,
            Pageable pageable);
}