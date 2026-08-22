package com.restaurant.system.printing.rules.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public class PrintingDisplayRuleRevisionResponse {
    public Long id;
    public Integer revision_number;
    public String status;
    public String schema_version;
    public String lifecycle_result;

    @JsonProperty("fingerprint_sha256")
    public String fingerprint_sha256;

    public String source_reference;
    public String summary;
    public JsonNode content;
    public LocalDateTime created_at;
    public LocalDateTime updated_at;
    public LocalDateTime published_at;
}
