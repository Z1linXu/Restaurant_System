package com.restaurant.system.menu.template;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class TemplateMenuModels {
    private TemplateMenuModels() {}
    public static class Groups {
        public List<Group> groups = new ArrayList<>();
        public Long menu_revision;
    }
    public static class Group {
        public String group_instance_code;
        public String name;
        public String kind = "CUSTOM";
        public String selection_mode = "SINGLE";
        public boolean required;
        public int sort_order;
        public boolean is_active = true;
        public String source_library_code;
        public List<Long> default_option_ids = new ArrayList<>();
        public List<Option> options = new ArrayList<>();
    }
    public static class Option {
        public Long id;
        public String option_type;
        public String option_code;
        public String option_group;
        public Long parent_option_id;
        public int sort_order;
        public String name_zh;
        public String name_en;
        public BigDecimal price_delta = BigDecimal.ZERO;
        public boolean is_active = true;
        public boolean is_default;
        public Long source_option_id;
    }
}
