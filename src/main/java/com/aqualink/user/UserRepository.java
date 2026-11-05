package com.aqualink.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByPhoneE164(String phoneE164);

	@Query(value = """
		SELECT r.code
		FROM user_roles ur
		JOIN roles r ON r.id = ur.role_id
		WHERE ur.user_id = :userId
		""", nativeQuery = true)
	List<String> findRoleCodesByUserId(@Param("userId") Long userId);

	@Query(value = """
		SELECT COUNT(*) > 0
		FROM user_roles ur
		JOIN roles r ON r.id = ur.role_id
				JOIN service_points sp ON sp.id = :servicePointId
		WHERE ur.user_id = :userId
		  AND r.code IN ('COMMUNITY_MANAGER', 'ADMIN')
					AND (ur.community_id IS NULL OR ur.community_id = sp.community_id)
		""", nativeQuery = true)
	boolean canManageServicePoint(
			@Param("userId") Long userId,
			@Param("servicePointId") Long servicePointId);
}