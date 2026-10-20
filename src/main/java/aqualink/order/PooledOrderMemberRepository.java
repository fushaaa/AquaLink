package com.aqualink.order;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PooledOrderMemberRepository
        extends JpaRepository<PooledOrderMember, Long> {

    List<PooledOrderMember> findByPooledOrderIdOrderByIdAsc(
            Long pooledOrderId);

    boolean existsBySourceRequestId(Long sourceRequestId);

    @Query("""
        select coalesce(sum(m.quantityL), 0)
        from PooledOrderMember m
        where m.pooledOrder.id = :poolId
        """)
    Long sumQuantity(@Param("poolId") Long poolId);
}