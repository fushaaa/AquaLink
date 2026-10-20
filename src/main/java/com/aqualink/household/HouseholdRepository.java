package com.aqualink.household;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface HouseholdRepository
        extends JpaRepository<Household, Long> {

    Optional<Household> findByPublicId(String publicId);

    // Implement this using household_users and users.
    // The service must verify membership before creating an order.
}