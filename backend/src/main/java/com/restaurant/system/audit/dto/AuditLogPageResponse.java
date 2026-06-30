package com.restaurant.system.audit.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class AuditLogPageResponse {

    public List<AuditLogResponse> records;

    public int page;

    public int size;

    @JsonProperty("total_count")
    public long totalCount;
}
