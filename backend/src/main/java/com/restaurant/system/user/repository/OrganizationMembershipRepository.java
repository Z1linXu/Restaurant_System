package com.restaurant.system.user.repository;

import com.restaurant.system.user.entity.OrganizationMembership;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationMembershipRepository extends JpaRepository<OrganizationMembership, Long> {

    List<OrganizationMembership> findAllByUserIdAndIsActiveTrueOrderByOrganizationIdAsc(Long userId);

    Optional<OrganizationMembership> findFirstByUserIdAndOrganizationId(Long userId, Long organizationId);

    boolean existsByUserIdAndOrganizationIdAndIsActiveTrue(Long userId, Long organizationId);
}
