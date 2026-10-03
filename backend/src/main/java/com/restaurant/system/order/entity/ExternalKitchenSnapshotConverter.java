package com.restaurant.system.order.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.order.dto.ExternalKitchenSnapshot;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class ExternalKitchenSnapshotConverter implements AttributeConverter<ExternalKitchenSnapshot, String> {
    private static final ObjectMapper JSON = new ObjectMapper();
    public String convertToDatabaseColumn(ExternalKitchenSnapshot value) {
        if (value == null) return null;
        try { return JSON.writeValueAsString(value); }
        catch (Exception ex) { throw new IllegalStateException("External kitchen snapshot encoding failed"); }
    }
    public ExternalKitchenSnapshot convertToEntityAttribute(String value) {
        if (value == null) return null;
        try { return JSON.readValue(value, ExternalKitchenSnapshot.class); }
        catch (Exception ex) { throw new IllegalStateException("External kitchen snapshot decoding failed"); }
    }
}
