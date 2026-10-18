package com.restaurant.system.menu.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.menu.dto.ItemCustomizationConfiguration;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class ItemCustomizationConfigurationConverter implements AttributeConverter<ItemCustomizationConfiguration, String> {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(ItemCustomizationConfiguration value) {
        if (value == null) return null;
        try { return JSON.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalArgumentException("MENU_CUSTOMIZATION_INVALID", e); }
    }

    @Override
    public ItemCustomizationConfiguration convertToEntityAttribute(String value) {
        if (value == null) return null;
        try { return JSON.readValue(value, ItemCustomizationConfiguration.class); }
        catch (JsonProcessingException e) { throw new IllegalArgumentException("MENU_CUSTOMIZATION_INVALID", e); }
    }
}
