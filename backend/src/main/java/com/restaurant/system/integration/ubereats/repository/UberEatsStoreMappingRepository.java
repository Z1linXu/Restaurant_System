package com.restaurant.system.integration.ubereats.repository;

import com.restaurant.system.integration.ubereats.entity.UberEatsStoreMapping;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UberEatsStoreMappingRepository extends JpaRepository<UberEatsStoreMapping, Long> {
    @org.springframework.data.jpa.repository.Lock(
            jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query(
            "select s from UberEatsStoreMapping s where s.id = :id")
    Optional<UberEatsStoreMapping> lock(
            @org.springframework.data.repository.query.Param("id") Long id);

    Optional<UberEatsStoreMapping> findByEnvironmentAndUberStoreId(
            String environment, String uberStoreId);

    Optional<UberEatsStoreMapping> findByEnvironmentAndStoreId(String environment, Long storeId);
}
