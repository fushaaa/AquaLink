package com.aqualink.household;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface HouseholdRepository
        extends JpaRepository<Household, Long> {

    Optional<Household> findByPublicId(String publicId);

    @Query(value = """
        SELECT h.*
        FROM households h
        JOIN household_users hu ON hu.household_id = h.id
        WHERE hu.user_id = :userId
        ORDER BY hu.is_primary DESC, h.id ASC
        LIMIT 1
        """, nativeQuery = true)
    Optional<Household> findPrimaryByMemberUserId(@Param("userId") Long userId);

    @Query(value = """
        SELECT h.*
        FROM households h
        JOIN household_users hu ON hu.household_id = h.id
        WHERE hu.user_id = :userId
        ORDER BY hu.is_primary DESC, h.id ASC
        """, nativeQuery = true)
    java.util.List<Household> findAllByMemberUserId(@Param("userId") Long userId);

    @Query(value = """
        SELECT COUNT(*) > 0
        FROM household_users
        WHERE household_id = :householdId AND user_id = :userId
        """, nativeQuery = true)
    boolean isMember(
            @Param("householdId") Long householdId,
            @Param("userId") Long userId);
}