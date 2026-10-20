package com.aqualink.provider;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WaterProviderRepository
        extends JpaRepository<WaterProvider, Long> {

    List<WaterProvider> findByActiveTrueAndVerificationStatusAndAvailabilityAndTripCapacityLGreaterThanEqualOrderByRatingAvgDescPricePerLitreAsc(
            VerificationStatus verificationStatus,
            Availability availability,
            int minimumCapacity,
            Pageable pageable);
}