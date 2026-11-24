package com.restaurant.system.menu.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;

public class MenuItemSizeConfigurationRequest {

    public List<String> enabled_size_codes = new ArrayList<>();
    public String default_size_code;
    /** Optional absolute portion prices. Omission preserves existing item prices. */
    public Map<String, BigDecimal> portion_prices;
}
