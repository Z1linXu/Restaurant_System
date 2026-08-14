package com.restaurant.system.owner.profile;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoreProfileArtifactRepository extends JpaRepository<StoreProfileArtifactEntity, Long> {

    @Query("""
        select artifact from StoreProfileArtifactEntity artifact
        where artifact.profile_version_id = :profileVersionId
        order by artifact.artifact_type asc, artifact.artifact_code asc
        """)
    List<StoreProfileArtifactEntity> findAllByProfileVersionIdOrderByArtifactTypeAscArtifactCodeAsc(
        @Param("profileVersionId") Long profileVersionId
    );
}
