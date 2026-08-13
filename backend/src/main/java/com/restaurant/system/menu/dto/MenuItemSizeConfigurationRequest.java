package com.restaurant.system.menu.dto;

import java.util.ArrayList;
import java.util.List;

public class MenuItemSizeConfigurationRequest {

    public List<String> enabled_size_codes = new ArrayList<>();
    public String default_size_code;
}
