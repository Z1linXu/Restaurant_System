package com.restaurant.system.station.repository;

import com.restaurant.system.station.entity.Station;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StationRepository extends JpaRepository<Station, Long> {

    @Query("""
        select s from Station s
        where s.store_id = :storeId and s.is_active = true
        order by s.sort_order asc, s.id asc
        """)
    List<Station> findActiveStationsByStoreId(@Param("storeId") Long storeId);

    @Query("""
        select s from Station s
        where s.id = :stationId and s.store_id = :storeId and s.is_active = true
        """)
    Station findActiveStationByIdAndStoreId(@Param("stationId") Long stationId, @Param("storeId") Long storeId);

    @Query("""
        select s from Station s
        where s.code = :stationCode and s.store_id = :storeId and s.is_active = true
        """)
    Station findActiveStationByCodeAndStoreId(@Param("stationCode") String stationCode, @Param("storeId") Long storeId);
}
