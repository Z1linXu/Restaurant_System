package com.restaurant.system.modules.dto;

import java.util.List;

public class StoreModuleUpdateRequest {
    public Long store_id;
    public List<ModuleUpdate> modules;

    public static class ModuleUpdate {
        public String module_key;
        public Boolean enabled;
    }
}
