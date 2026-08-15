package com.restaurant.system.printing.rules.dto;

import com.fasterxml.jackson.databind.JsonNode;

public class PrintingDisplayRuleDraftRequest {
    public Long store_id;
    public JsonNode content;
    public String summary;
}
