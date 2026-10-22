package com.restaurant.system.printing.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.restaurant.system.printing.repository.PrintJobRepository;
import com.restaurant.system.user.repository.StoreRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PrintingRemoveSettingsTest {
    @Test
    void settingsDryRunUsesEditableRevisionAndReportsUnboundLegacyWithoutWriting() {
        var sets = mock(PrintingDisplayRuleSetRepository.class);
        var revisions = mock(PrintingDisplayRuleRevisionRepository.class);
        var stores = mock(StoreRepository.class);
        var vocabulary = mock(PrintingRemoveVocabulary.class);
        var service = new PrintingDisplayRuleServiceImpl(sets, revisions, stores, mock(PrintJobRepository.class),
            mock(PrintingAddonVocabulary.class), mock(jakarta.persistence.EntityManager.class));
        service.setRemoveVocabulary(vocabulary);
        var set = new PrintingDisplayRuleSet();
        set.id = 1L; set.store_id = 2L; set.active_revision_id = 10L;
        when(stores.existsById(2L)).thenReturn(true);
        when(sets.findByStoreId(2L)).thenReturn(Optional.of(set));
        var active = new PrintingDisplayRuleRevision();
        active.id = 10L; active.revision_number = 6; active.status = "PUBLISHED";
        active.content_json = PrintingRemoveAliasesTest.content("[[\"beef\",\"走牛\"]]").toString();
        var draft = new PrintingDisplayRuleRevision();
        draft.id = 11L; draft.revision_number = 7; draft.status = "DRAFT";
        draft.content_json = PrintingRemoveAliasesTest.content("[[\"beef\",\"牛A\"],[\"remove_beef\",\"牛B\"],[\"carrot_slice\",\"片旧值\"]]").toString();
        when(revisions.findAllByRuleSetIdOrderByRevisionNumberDesc(1L)).thenReturn(List.of(draft, active));
        when(vocabulary.entries(eq(2L), any())).thenReturn(List.of(new PrintingRemoveVocabulary.Entry("beef", "走牛", "走牛", "MENU", List.of("beef", "remove_beef"))));
        var result = service.getSettings(2L);
        assertThat(result.remove_alias_proposal_revision_id).isEqualTo(11L);
        assertThat(result.remove_alias_conflicts).extracting(PrintingRemoveAliases.Conflict::code).containsExactly("beef");
        assertThat(result.legacy_remove_aliases).containsExactly(new PrintingRemoveAliases.Alias("carrot_slice", "片旧值"));
        assertThat(result.active_revision.content.toString()).isEqualTo(active.content_json);
        verify(revisions, never()).save(any());
        verify(sets, never()).save(any());
    }
}
