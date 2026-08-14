package com.restaurant.system.menu.dto;

import java.util.ArrayList;
import java.util.List;

public class StoreComboConfigurationUpdateRequest {

    public Long store_id;
    public List<GroupUpdate> groups = new ArrayList<>();
    public List<ComponentUpdate> components = new ArrayList<>();

    public static class GroupUpdate {
        public Long group_id;
        public String group_code;
        public String name_zh;
        public String name_en;
        public String selection_rule;
        public Boolean required;
        public Boolean enabled;
        public Integer display_order;
        public String default_component_code;
        public List<ComponentUpdate> components = new ArrayList<>();
    }

    public static class ComponentUpdate {
        public Long id;
        public Long group_id;
        public String component_group;
        public String component_code;
        public String name_zh;
        public String name_en;
        public Boolean enabled;
        public Integer display_order;
        public Boolean is_default;
        public Long linked_menu_item_id;
        public String business_behavior;
    }
}
