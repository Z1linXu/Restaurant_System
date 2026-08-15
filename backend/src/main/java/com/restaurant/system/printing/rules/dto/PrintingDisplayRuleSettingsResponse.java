package com.restaurant.system.printing.rules.dto;

import java.util.List;

public class PrintingDisplayRuleSettingsResponse {
    public Long store_id;
    public Long rule_set_id;
    public Long active_revision_id;
    public PrintingDisplayRuleRevisionResponse active_revision;
    public PrintingDisplayRuleRevisionResponse draft_revision;
    public List<PrintingDisplayRuleRevisionResponse> revisions;
}
