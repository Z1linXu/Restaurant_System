package com.restaurant.system.menu.dto;

import java.util.ArrayList;
import java.util.List;

public class StoreComboConfigurationResponse {

    public Long store_id;
    public Long menu_revision;
    public List<GroupResponse> groups = new ArrayList<>();

    public static class GroupResponse {
        public String component_group;
        public String name_zh;
        public String name_en;
        public String default_component_code;
        public List<ComponentResponse> components = new ArrayList<>();
    }

    public static class ComponentResponse {
        public String component_group;
        public String component_code;
        public String name_zh;
        public String name_en;
        public Boolean enabled;
        public Integer display_order;
        public Boolean is_default;
    }
}
