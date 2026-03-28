package com.restaurant.system.user.repository;

import com.restaurant.system.user.entity.Store;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreRepository extends JpaRepository<Store, Long> {
}
