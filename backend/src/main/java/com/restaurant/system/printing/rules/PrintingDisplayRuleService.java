package com.restaurant.system.printing.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.restaurant.system.printing.entity.PrintJob;
import com.restaurant.system.printing.rules.dto.PrintingDisplayRuleDraftRequest;
import com.restaurant.system.printing.rules.dto.PrintingDisplayRulePreviewRequest;
import com.restaurant.system.printing.rules.dto.PrintingDisplayRulePreviewResponse;
import com.restaurant.system.printing.rules.dto.PrintingDisplayRuleRevisionResponse;
import com.restaurant.system.printing.rules.dto.PrintingDisplayRuleSettingsResponse;
import com.restaurant.system.printing.rules.dto.PrintingDisplayRuleValidationResponse;

public interface PrintingDisplayRuleService {

    PrintingDisplayRuleSettingsResponse getSettings(Long storeId);

    PrintingDisplayRuleRevisionResponse saveDraft(PrintingDisplayRuleDraftRequest request);

    PrintingDisplayRuleRevisionResponse publishDraft(Long storeId, Long revisionId);

    PrintingDisplayRuleValidationResponse validate(Long storeId, JsonNode content);

    PrintingDisplayRulePreviewResponse preview(PrintingDisplayRulePreviewRequest request);

    PrintingDisplayRuleContext activeContext(Long storeId);

    PrintingDisplayRuleContext contextForJob(PrintJob job);

    PrintingDisplayRuleContext historicalContextForOrder(Long storeId, Long orderId, String moduleCode);
}
