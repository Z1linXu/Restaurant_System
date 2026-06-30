package com.restaurant.system.user.repository;

import com.restaurant.system.user.entity.Role;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findFirstByCodeIgnoreCase(String code);
}
