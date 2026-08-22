package com.restaurant.system.printing.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.owner.profile.StoreProfileCanonicalJson;
import com.restaurant.system.printing.rules.dto.PrintingDisplayRuleDraftRequest;
import com.restaurant.system.printing.rules.dto.PrintingDisplayRulePreviewRequest;
import com.restaurant.system.printing.rules.dto.PrintingDisplayRuleRevisionResponse;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.repository.StoreRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ContextConfiguration(classes = PrintingDisplayRuleServiceLifecycleIntegrationTest.JpaSliceConfiguration.class)
@Import(PrintingDisplayRuleServiceImpl.class)
class PrintingDisplayRuleServiceLifecycleIntegrationTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = MybatisPlusAutoConfiguration.class)
    @EntityScan(basePackages = "com.restaurant.system")
    @EnableJpaRepositories(basePackages = "com.restaurant.system")
    static class JpaSliceConfiguration {
    }

    @Autowired private PrintingDisplayRuleServiceImpl service;
    @Autowired private StoreRepository storeRepository;
    @SpyBean private PrintingDisplayRuleSetRepository ruleSetRepository;
    @Autowired private PrintingDisplayRuleRevisionRepository revisionRepository;
    @Autowired private EntityManager entityManager;

    @Test
    void publishesFirstSecondAndThirdRevisionsIncludingAToBToARollback() {
        Store store = createStore("RULE_ROLLBACK");
        JsonNode contentA = content("+牛筋");
        JsonNode contentB = content("+牛筋鸡");

        PrintingDisplayRuleRevisionResponse first = publish(store.id, contentA);
        PrintingDisplayRuleRevisionResponse second = publish(store.id, contentB);
        PrintingDisplayRuleRevisionResponse third = publish(store.id, contentA);

        assertThat(first.revision_number).isEqualTo(2);
        assertThat(second.revision_number).isEqualTo(3);
        assertThat(third.revision_number).isEqualTo(4);
        assertThat(third.id).isNotEqualTo(first.id);
        assertThat(third.fingerprint_sha256).isEqualTo(first.fingerprint_sha256);
        assertThat(third.lifecycle_result).isEqualTo("PUBLISHED");

        PrintingDisplayRuleSet ruleSet = ruleSetRepository.findByStoreId(store.id).orElseThrow();
        assertThat(ruleSet.active_revision_id).isEqualTo(third.id);
        assertThat(revisionRepository.findAllByRuleSetIdOrderByRevisionNumberDesc(ruleSet.id))
            .extracting(revision -> revision.status)
            .containsOnly("PUBLISHED");
    }

    @Test
    void activeContentSaveIsAnExplicitNoOpAndCreatesNoDraft() {
        Store store = createStore("RULE_NOOP");
        JsonNode contentA = content("+牛筋");
        PrintingDisplayRuleRevisionResponse published = publish(store.id, contentA);
        PrintingDisplayRuleRevision stored = revisionRepository.findById(published.id).orElseThrow();
        stored.content_json = " \n" + stored.content_json + "\n ";
        revisionRepository.saveAndFlush(stored);
        int revisionCount = service.getSettings(store.id).revisions.size();

        PrintingDisplayRuleRevisionResponse result = service.saveDraft(request(store.id, contentA));

        assertThat(result.id).isEqualTo(published.id);
        assertThat(result.status).isEqualTo("PUBLISHED");
        assertThat(result.lifecycle_result).isEqualTo("ALREADY_ACTIVE");
        assertThat(service.getSettings(store.id).draft_revision).isNull();
        assertThat(service.getSettings(store.id).revisions).hasSize(revisionCount);
    }

    @Test
    void existingDraftIsUpdatedInsteadOfCreatingAnotherDraft() {
        Store store = createStore("RULE_SINGLE_DRAFT");
        PrintingDisplayRuleRevisionResponse firstSave = service.saveDraft(request(store.id, content("+牛筋")));
        PrintingDisplayRuleRevisionResponse secondSave = service.saveDraft(request(store.id, content("+牛筋鸡")));

        assertThat(secondSave.id).isEqualTo(firstSave.id);
        assertThat(secondSave.revision_number).isEqualTo(firstSave.revision_number);
        assertThat(secondSave.lifecycle_result).isEqualTo("DRAFT_SAVED");
        assertThat(service.getSettings(store.id).revisions)
            .filteredOn(revision -> "DRAFT".equals(revision.status))
            .hasSize(1);
        assertThat(service.getSettings(store.id).draft_revision.content.toString()).contains("+牛筋鸡");
    }

    @Test
    void savingActiveContentDiscardsAnExistingMutableDraftWithoutTouchingHistory() {
        Store store = createStore("RULE_NOOP_WITH_DRAFT");
        PrintingDisplayRuleRevisionResponse active = publish(store.id, content("+牛筋"));
        service.saveDraft(request(store.id, content("+牛筋鸡")));

        PrintingDisplayRuleRevisionResponse result = service.saveDraft(request(store.id, content("+牛筋")));

        assertThat(result.id).isEqualTo(active.id);
        assertThat(result.lifecycle_result).isEqualTo("ALREADY_ACTIVE");
        assertThat(service.getSettings(store.id).draft_revision).isNull();
        assertThat(service.getSettings(store.id).revisions)
            .filteredOn(revision -> "PUBLISHED".equals(revision.status))
            .hasSize(2);
    }

    @Test
    void stalePublishedRevisionCannotBePublishedAgain() {
        Store store = createStore("RULE_STALE");
        PrintingDisplayRuleRevisionResponse published = publish(store.id, content("+牛筋"));

        assertThatThrownBy(() -> service.publishDraft(store.id, published.id))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Only DRAFT");
        assertThat(ruleSetRepository.findByStoreId(store.id).orElseThrow().active_revision_id)
            .isEqualTo(published.id);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void pointerWriteFailureRollsBackPublishedTransitionAndKeepsOldActivePointer() {
        Store store = createStore("RULE_PUBLISH_ROLLBACK");
        PrintingDisplayRuleRevisionResponse active = publish(store.id, content("+牛筋"));
        PrintingDisplayRuleRevisionResponse draft = service.saveDraft(request(store.id, content("+牛筋鸡")));

        doThrow(new DataIntegrityViolationException("active pointer write conflict"))
            .when(ruleSetRepository)
            .saveAndFlush(any(PrintingDisplayRuleSet.class));

        assertThatThrownBy(() -> service.publishDraft(store.id, draft.id))
            .isInstanceOf(PrintingDisplayRuleConflictException.class)
            .extracting(exception -> ((PrintingDisplayRuleConflictException) exception).getErrorCode())
            .isEqualTo("PRINTING_DISPLAY_RULE_PUBLISH_CONFLICT");

        entityManager.clear();
        assertThat(revisionRepository.findById(draft.id).orElseThrow().status).isEqualTo("DRAFT");
        assertThat(revisionRepository.findById(draft.id).orElseThrow().published_at).isNull();
        assertThat(ruleSetRepository.findByStoreId(store.id).orElseThrow().active_revision_id)
            .isEqualTo(active.id);
    }

    @Test
    void duplicateLegacyDraftStateFailsWithAStableBusinessConflict() {
        Store store = createStore("RULE_DUPLICATE_DRAFT");
        service.saveDraft(request(store.id, content("+牛筋")));
        PrintingDisplayRuleSet ruleSet = ruleSetRepository.findByStoreId(store.id).orElseThrow();
        PrintingDisplayRuleRevision duplicate = revision("DRAFT", ruleSet.id, 3, content("+牛筋鸡"));
        revisionRepository.saveAndFlush(duplicate);

        assertThatThrownBy(() -> service.saveDraft(request(store.id, content("+牛筋面"))))
            .isInstanceOf(PrintingDisplayRuleConflictException.class)
            .extracting(exception -> ((PrintingDisplayRuleConflictException) exception).getErrorCode())
            .isEqualTo("PRINTING_DISPLAY_RULE_MULTIPLE_DRAFTS");
    }

    @Test
    void publishRejectsAnotherStoreDraftWithoutMovingEitherActivePointer() {
        Store firstStore = createStore("RULE_STORE_ONE");
        Store secondStore = createStore("RULE_STORE_TWO");
        PrintingDisplayRuleRevisionResponse firstActive = publish(firstStore.id, content("+一店"));
        PrintingDisplayRuleRevisionResponse secondDraft = service.saveDraft(request(secondStore.id, content("+二店")));
        Long secondActiveId = ruleSetRepository.findByStoreId(secondStore.id).orElseThrow().active_revision_id;

        assertThatThrownBy(() -> service.publishDraft(firstStore.id, secondDraft.id))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("does not belong to store");
        assertThat(ruleSetRepository.findByStoreId(firstStore.id).orElseThrow().active_revision_id)
            .isEqualTo(firstActive.id);
        assertThat(ruleSetRepository.findByStoreId(secondStore.id).orElseThrow().active_revision_id)
            .isEqualTo(secondActiveId);
        assertThat(revisionRepository.findById(secondDraft.id).orElseThrow().status).isEqualTo("DRAFT");
    }

    @Test
    void previewAndPublishedRuntimeContextResolveTheSameRollbackContent() {
        Store store = createStore("RULE_PREVIEW_RUNTIME");
        JsonNode contentA = content("+牛筋");
        publish(store.id, contentA);
        publish(store.id, content("+牛筋鸡"));
        publish(store.id, contentA);

        PrintingDisplayRulePreviewRequest previewRequest = new PrintingDisplayRulePreviewRequest();
        previewRequest.store_id = store.id;
        previewRequest.content = contentA;
        previewRequest.modifier_add_codes = List.of("addon_beef_tendon");
        previewRequest.modifier_remove_codes = List.of();

        var preview = service.preview(previewRequest);
        PrintingDisplayRuleContext runtime = service.activeContext(store.id);

        assertThat(preview.grab_preview).contains("+牛筋");
        assertThat(preview.hot_kitchen_preview).contains("+牛筋");
        assertThat(runtime.resolveModifierToken("MODIFIER_ADD", "addon_beef_tendon", null))
            .isEqualTo("+牛筋");
        assertThat(runtime.fingerprintSha256()).isEqualTo(preview.fingerprint_sha256);
    }

    private PrintingDisplayRuleRevisionResponse publish(Long storeId, JsonNode content) {
        PrintingDisplayRuleRevisionResponse draft = service.saveDraft(request(storeId, content));
        assertThat(draft.status).isEqualTo("DRAFT");
        return service.publishDraft(storeId, draft.id);
    }

    private PrintingDisplayRuleDraftRequest request(Long storeId, JsonNode content) {
        PrintingDisplayRuleDraftRequest request = new PrintingDisplayRuleDraftRequest();
        request.store_id = storeId;
        request.content = content;
        request.summary = "Lifecycle integration test";
        return request;
    }

    private JsonNode content(String addonToken) {
        return StoreProfileCanonicalJson.parse("""
            {
              "schema_version": "PRINTING_DISPLAY_RULES_V1",
              "outputs": ["GRAB", "FRONTDESK_RECEIPT", "HOT_KITCHEN"],
              "item_aliases": [],
              "dictionaries": {
                "SIZE": [],
                "NOODLE_TYPE": [],
                "SPICINESS": [],
                "MODIFIER_ADD": [["addon_beef_tendon", "%s"]],
                "MODIFIER_REMOVE": []
              },
              "conditional_overrides": [],
              "formatting": {}
            }
            """.formatted(addonToken));
    }

    private PrintingDisplayRuleRevision revision(String status, Long ruleSetId, int number, JsonNode content) {
        LocalDateTime now = LocalDateTime.now();
        String canonical = StoreProfileCanonicalJson.canonicalize(content);
        PrintingDisplayRuleRevision revision = new PrintingDisplayRuleRevision();
        revision.rule_set_id = ruleSetId;
        revision.revision_number = number;
        revision.status = status;
        revision.schema_version = PrintingDisplayRuleDefaults.SCHEMA_VERSION;
        revision.content_json = canonical;
        revision.fingerprint_sha256 = StoreProfileCanonicalJson.sha256(canonical);
        revision.source_reference = "TEST";
        revision.created_at = now;
        revision.updated_at = now;
        return revision;
    }

    private Store createStore(String code) {
        LocalDateTime now = LocalDateTime.now();
        Store store = new Store();
        store.organization_id = 1L;
        store.code = code;
        store.name = code;
        store.status = "active";
        store.printing_enabled = false;
        store.printing_mode = "MOCK";
        store.menu_revision = 1L;
        store.menu_updated_at = now;
        store.created_at = now;
        store.updated_at = now;
        return storeRepository.saveAndFlush(store);
    }
}
