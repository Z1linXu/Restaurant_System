package com.restaurant.system.menu.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Optional Store-owned rules. Null preserves the existing menu behavior. */
public class ItemCustomizationConfiguration {
    public Integer schema_version = 1;
    public List<OptionGroup> option_groups = new ArrayList<>();
    public Map<String, Integer> addon_max_quantities = new LinkedHashMap<>();
    public Combo combo;

    public static class OptionGroup {
        public String option_group;
        public Integer min_select;
        public Integer max_select;
        public Boolean distinct = true;
        public List<String> default_option_codes = new ArrayList<>();
    }

    public static class Combo {
        public BigDecimal fixed_price;
        public List<String> group_codes = new ArrayList<>();
    }
}
