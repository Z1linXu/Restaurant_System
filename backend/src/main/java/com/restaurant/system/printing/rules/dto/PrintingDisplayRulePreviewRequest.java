package com.restaurant.system.printing.rules.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public class PrintingDisplayRulePreviewRequest {
    public Long store_id;
    public JsonNode content;
    public String item_sku;
    public String item_name_zh;
    public String item_name_en;
    public String size_zh;
    public String noodle_type_zh;
    public String spiciness_zh;
    public List<String> modifier_add_codes;
    public List<String> modifier_remove_codes;
    public Boolean combo;
}
