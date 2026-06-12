package com.restaurant.system.production.repository;

import com.restaurant.system.production.entity.ProductionTask;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductionTaskRepository extends JpaRepository<ProductionTask, Long> {
}
