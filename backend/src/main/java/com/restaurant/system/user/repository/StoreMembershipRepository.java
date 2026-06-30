package com.restaurant.system.user.repository;

import com.restaurant.system.user.entity.StoreMembership;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreMembershipRepository extends JpaRepository<StoreMembership, Long> {

    List<StoreMembership> findAllByUserIdAndIsActiveTrueOrderByStoreIdAsc(Long userId);

    Optional<StoreMembership> findFirstByUserIdAndStoreId(Long userId, Long storeId);

    boolean existsByUserIdAndStoreIdAndIsActiveTrue(Long userId, Long storeId);
}
