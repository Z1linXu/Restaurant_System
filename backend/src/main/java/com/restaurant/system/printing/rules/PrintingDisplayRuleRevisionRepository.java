package com.restaurant.system.printing.rules;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PrintingDisplayRuleRevisionRepository extends JpaRepository<PrintingDisplayRuleRevision, Long> {

    @Query("""
        select revision from PrintingDisplayRuleRevision revision
        where revision.rule_set_id = :ruleSetId
        order by revision.revision_number desc, revision.id desc
        """)
    List<PrintingDisplayRuleRevision> findAllByRuleSetIdOrderByRevisionNumberDesc(@Param("ruleSetId") Long ruleSetId);

    @Query("""
        select revision from PrintingDisplayRuleRevision revision
        where revision.rule_set_id = :ruleSetId and revision.status = 'DRAFT'
        order by revision.revision_number desc, revision.id desc
        """)
    List<PrintingDisplayRuleRevision> findDraftsByRuleSetId(@Param("ruleSetId") Long ruleSetId);

    @Query("""
        select coalesce(max(revision.revision_number), 0) from PrintingDisplayRuleRevision revision
        where revision.rule_set_id = :ruleSetId
        """)
    Integer findMaxRevisionNumber(@Param("ruleSetId") Long ruleSetId);

    @Query("""
        select revision from PrintingDisplayRuleRevision revision
        where revision.rule_set_id = :ruleSetId
          and revision.fingerprint_sha256 = :fingerprintSha256
        order by revision.revision_number desc, revision.id desc
        """)
    List<PrintingDisplayRuleRevision> findAllByRuleSetIdAndFingerprintSha256OrderByRevisionNumberDesc(
        @Param("ruleSetId") Long ruleSetId,
        @Param("fingerprintSha256") String fingerprintSha256
    );
}
