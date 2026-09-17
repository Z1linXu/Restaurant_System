package com.restaurant.system.integration.ubereats.repository;

import com.restaurant.system.integration.ubereats.entity.UberEatsStoreMapping;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UberEatsStoreMappingRepository extends JpaRepository<UberEatsStoreMapping, Long> {
    Optional<UberEatsStoreMapping> findByEnvironmentAndUberStoreId(
            String environment, String uberStoreId);

    Optional<UberEatsStoreMapping> findByEnvironmentAndStoreId(String environment, Long storeId);
}
