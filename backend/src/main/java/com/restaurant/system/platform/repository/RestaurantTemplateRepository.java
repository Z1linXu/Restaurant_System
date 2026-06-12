package com.restaurant.system.platform.repository;

import com.restaurant.system.platform.entity.RestaurantTemplate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RestaurantTemplateRepository extends JpaRepository<RestaurantTemplate, Long> {
    RestaurantTemplate findByCode(String code);

    List<RestaurantTemplate> findAllByOrderByIdAsc();
}
