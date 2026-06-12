package com.restaurant.system.auth.repository;

import com.restaurant.system.auth.entity.RolePermission;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {

    List<RolePermission> findAllByRoleIdAndIsAllowedTrue(Long roleId);
}
