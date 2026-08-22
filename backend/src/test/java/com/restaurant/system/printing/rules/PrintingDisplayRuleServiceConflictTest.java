package com.restaurant.system.printing.rules;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.restaurant.system.owner.profile.StoreProfileCanonicalJson;
import com.restaurant.system.printing.repository.PrintJobRepository;
import com.restaurant.system.printing.rules.dto.PrintingDisplayRuleDraftRequest;
import com.restaurant.system.user.repository.StoreRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class PrintingDisplayRuleServiceConflictTest {

    @Mock private PrintingDisplayRuleSetRepository ruleSetRepository;
    @Mock private PrintingDisplayRuleRevisionRepository revisionRepository;
    @Mock private StoreRepository storeRepository;
    @Mock private PrintJobRepository printJobRepository;

    private PrintingDisplayRuleServiceImpl service;
    private PrintingDisplayRuleSet ruleSet;

    @BeforeEach
    void setUp() {
        service = new PrintingDisplayRuleServiceImpl(
            ruleSetRepository,
            revisionRepository,
            storeRepository,
            printJobRepository
        );
        ruleSet = new PrintingDisplayRuleSet();
        ruleSet.id = 10L;
        ruleSet.store_id = 1L;
        ruleSet.active_revision_id = 100L;
        when(storeRepository.existsById(1L)).thenReturn(true);
        when(ruleSetRepository.findByStoreIdForUpdate(1L)).thenReturn(Optional.of(ruleSet));
    }

    @Test
    void draftConstraintFailureBecomesStableBusinessConflict() {
        PrintingDisplayRuleRevision active = revision(100L, 1, "PUBLISHED", PrintingDisplayRuleDefaults.DEFAULT_CONTENT_JSON);
        when(revisionRepository.findDraftsByRuleSetId(10L)).thenReturn(List.of());
        when(revisionRepository.findById(100L)).thenReturn(Optional.of(active));
        when(revisionRepository.findMaxRevisionNumber(10L)).thenReturn(1);
        when(revisionRepository.saveAndFlush(any(PrintingDisplayRuleRevision.class)))
            .thenThrow(new DataIntegrityViolationException("single draft conflict"));

        PrintingDisplayRuleDraftRequest request = new PrintingDisplayRuleDraftRequest();
        request.store_id = 1L;
        request.content = StoreProfileCanonicalJson.parse(minimalContent("+牛筋"));

        assertThatThrownBy(() -> service.saveDraft(request))
            .isInstanceOf(PrintingDisplayRuleConflictException.class)
            .extracting(exception -> ((PrintingDisplayRuleConflictException) exception).getErrorCode())
            .isEqualTo("PRINTING_DISPLAY_RULE_DRAFT_CONFLICT");
    }

    @Test
    void publishConstraintFailureBecomesStableBusinessConflict() {
        PrintingDisplayRuleRevision draft = revision(101L, 2, "DRAFT", minimalContent("+牛筋"));
        when(revisionRepository.findById(101L)).thenReturn(Optional.of(draft));
        when(revisionRepository.saveAndFlush(draft))
            .thenThrow(new DataIntegrityViolationException("publish conflict"));

        assertThatThrownBy(() -> service.publishDraft(1L, 101L))
            .isInstanceOf(PrintingDisplayRuleConflictException.class)
            .extracting(exception -> ((PrintingDisplayRuleConflictException) exception).getErrorCode())
            .isEqualTo("PRINTING_DISPLAY_RULE_PUBLISH_CONFLICT");
    }

    private PrintingDisplayRuleRevision revision(Long id, int number, String status, String content) {
        String canonical = StoreProfileCanonicalJson.canonicalize(content);
        PrintingDisplayRuleRevision revision = new PrintingDisplayRuleRevision();
        revision.id = id;
        revision.rule_set_id = ruleSet.id;
        revision.revision_number = number;
        revision.status = status;
        revision.schema_version = PrintingDisplayRuleDefaults.SCHEMA_VERSION;
        revision.content_json = canonical;
        revision.fingerprint_sha256 = StoreProfileCanonicalJson.sha256(canonical);
        revision.created_at = LocalDateTime.now();
        revision.updated_at = revision.created_at;
        return revision;
    }

    private String minimalContent(String token) {
        return """
            {
              "schema_version": "PRINTING_DISPLAY_RULES_V1",
              "outputs": ["GRAB", "FRONTDESK_RECEIPT", "HOT_KITCHEN"],
              "item_aliases": [],
              "dictionaries": {
                "SIZE": [], "NOODLE_TYPE": [], "SPICINESS": [],
                "MODIFIER_ADD": [["addon_beef_tendon", "%s"]],
                "MODIFIER_REMOVE": []
              },
              "conditional_overrides": [],
              "formatting": {}
            }
            """.formatted(token);
    }
}
