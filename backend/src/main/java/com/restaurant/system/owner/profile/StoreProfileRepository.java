package com.restaurant.system.owner.profile;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoreProfileRepository extends JpaRepository<StoreProfileEntity, Long> {

    @Query("""
        select profile from StoreProfileEntity profile
        where profile.profile_code = :profileCode
        """)
    Optional<StoreProfileEntity> findByProfileCode(@Param("profileCode") String profileCode);

    @Query("""
        select profile from StoreProfileEntity profile
        order by profile.profile_code asc
        """)
    List<StoreProfileEntity> findAllByOrderByProfileCodeAsc();
}
