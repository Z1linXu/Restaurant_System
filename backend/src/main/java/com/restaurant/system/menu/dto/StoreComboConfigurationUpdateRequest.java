package com.restaurant.system.menu.dto;

import java.util.ArrayList;
import java.util.List;

public class StoreComboConfigurationUpdateRequest {

    public Long store_id;
    public List<ComponentUpdate> components = new ArrayList<>();

    public static class ComponentUpdate {
        public String component_group;
        public String component_code;
        public Boolean enabled;
    }
}
