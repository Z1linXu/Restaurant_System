package com.restaurant.system.printing.rules.dto;

import java.util.List;

public class PrintingDisplayRuleSettingsResponse {
    public Long store_id;
    public Long rule_set_id;
    public Long active_revision_id;
    public PrintingDisplayRuleRevisionResponse active_revision;
    public PrintingDisplayRuleRevisionResponse draft_revision;
    public List<PrintingDisplayRuleRevisionResponse> revisions;
    public List<com.restaurant.system.printing.rules.PrintingAddonVocabulary.Entry> addon_aliases;
    public List<com.restaurant.system.printing.rules.PrintingRemoveVocabulary.Entry> remove_aliases;
    public List<com.restaurant.system.printing.rules.PrintingRemoveAliases.Conflict> remove_alias_conflicts;
    public List<com.restaurant.system.printing.rules.PrintingRemoveAliases.Alias> legacy_remove_aliases;
    public com.fasterxml.jackson.databind.JsonNode proposed_remove_aliases;
    public Long remove_alias_proposal_revision_id;
}
