package com.restaurant.system.staging.bootstrap.repository;

import com.restaurant.system.staging.bootstrap.entity.StagingSyntheticBootstrapRequest;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StagingSyntheticBootstrapRequestRepository
    extends JpaRepository<StagingSyntheticBootstrapRequest, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select request from StagingSyntheticBootstrapRequest request
        where request.runId = :runId
        """)
    Optional<StagingSyntheticBootstrapRequest> findForUpdate(@Param("runId") String runId);
}
