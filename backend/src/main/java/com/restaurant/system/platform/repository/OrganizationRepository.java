package com.restaurant.system.platform.repository;

import com.restaurant.system.platform.entity.Organization;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {
    Organization findByCode(String code);

    List<Organization> findAllByOrderByIdAsc();
}
