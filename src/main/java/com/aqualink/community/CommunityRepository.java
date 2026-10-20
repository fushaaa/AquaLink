package com.aqualink.community;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface CommunityRepository extends JpaRepository<Community, Long> {

    @Query("select c.id from Community c where c.status = :status")
    List<Long> findIdsByStatus(@Param("status") CommunityStatus status);
}