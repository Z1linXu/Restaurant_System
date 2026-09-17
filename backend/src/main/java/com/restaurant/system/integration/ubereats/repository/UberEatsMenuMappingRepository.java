package com.restaurant.system.integration.ubereats.repository;

import com.restaurant.system.integration.ubereats.entity.UberEatsMenuMapping;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UberEatsMenuMappingRepository extends JpaRepository<UberEatsMenuMapping, Long> {
    List<UberEatsMenuMapping> findAllByStoreMappingIdOrderByIdAsc(Long storeMappingId);
}
