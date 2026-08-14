package com.restaurant.system.owner.profile;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoreProfileVersionRepository extends JpaRepository<StoreProfileVersionEntity, Long> {

    @Query("""
        select version from StoreProfileVersionEntity version
        where version.profile_id = :profileId
        order by version.profile_version asc
        """)
    List<StoreProfileVersionEntity> findAllByProfileIdOrderByProfileVersionAsc(@Param("profileId") Long profileId);

    @Query("""
        select version from StoreProfileVersionEntity version
        where version.profile_id = :profileId
          and version.profile_version = :profileVersion
        """)
    Optional<StoreProfileVersionEntity> findByProfileIdAndProfileVersion(
        @Param("profileId") Long profileId,
        @Param("profileVersion") String profileVersion
    );
}
