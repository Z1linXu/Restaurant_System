package com.restaurant.system.integration.ubereats.repository;

import com.restaurant.system.integration.ubereats.entity.UberEatsOrder;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.*;

public interface UberEatsOrderRepository extends JpaRepository<UberEatsOrder, Long> {
    Optional<UberEatsOrder> findByEnvironmentAndUberOrderId(String environment, String uberOrderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from UberEatsOrder o where o.id = :id")
    Optional<UberEatsOrder> lock(@Param("id") Long id);

    long countByEnvironmentAndStoreIdAndMappingStatus(
            String environment, Long storeId, String mappingStatus);

    @Query(
            "select o from UberEatsOrder o where o.environment = :environment and o.storeId ="
                + " :storeId order by case when o.status in ('ACCEPTED','DENIED','CANCELLED') then"
                + " 1 else 0 end, o.id desc")
    List<UberEatsOrder> inbox(
            @Param("environment") String environment,
            @Param("storeId") Long storeId,
            Pageable page);

    @Query(
            "select o from UberEatsOrder o where o.environment=:env and o.storeId=:store and"
                + " (o.processingMode <> 'KITCHEN_MIRROR' or (coalesce(o.releasedAt,o.placedAt) >="
                + " :start and coalesce(o.releasedAt,o.placedAt) < :end) or (o.releasedAt is null"
                + " and o.placedAt is null and o.createdAt >= :createdStart and o.createdAt <"
                + " :createdEnd) or o.status in"
                + " ('WAITING_FOR_RELEASE','RELEASED_MAPPING_REQUIRED','MIRROR_READY','MIRROR_LOCAL_FAILED','LOCAL_REVIEW_REQUIRED','EDIT_REVIEW_REQUIRED','EXTERNAL_STATE_REVIEW_REQUIRED','CANCELLED_AFTER_RELEASE'))"
                + " order by o.id desc")
    List<UberEatsOrder> todayInbox(
            @Param("env") String env,
            @Param("store") Long store,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("createdStart") LocalDateTime createdStart,
            @Param("createdEnd") LocalDateTime createdEnd,
            Pageable page);

    List<UberEatsOrder> findByEnvironmentAndStoreIdOrderByIdDesc(
            String environment, Long storeId, Pageable page);

    @Query(
            "select o from UberEatsOrder o where o.environment = :env and o.status in"
                + " ('ACCEPTING','DENYING','UBER_ACCEPTED','LOCAL_FAILED','MIRROR_READY','MIRROR_LOCAL_FAILED','RELEASED_MAPPING_REQUIRED')"
                + " and o.nextAttemptAt <= :now order by o.id")
    List<UberEatsOrder> due(
            @Param("env") String env, @Param("now") LocalDateTime now, Pageable page);

    @Modifying
    @Query(
            value =
                    """
                    insert into uber_eats_orders(environment,store_mapping_id,store_id,uber_store_id,uber_order_id,uber_event_id,status,mapping_status,attempt_count,cancelled,edit_required,scheduled,next_attempt_at,created_at,updated_at,processing_mode)
                    values (:env,:mapping,:store,:uberStore,:orderId,:event,'RECEIVED','PENDING',0,false,false,false,:now,:now,:now,(select processing_mode from uber_eats_store_mappings where id=:mapping))
                    on conflict(environment,uber_order_id) do nothing
                    """,
            nativeQuery = true)
    int insertIfAbsent(
            @Param("env") String env,
            @Param("mapping") Long mapping,
            @Param("store") Long store,
            @Param("uberStore") String uberStore,
            @Param("orderId") String orderId,
            @Param("event") String event,
            @Param("now") LocalDateTime now);
}
