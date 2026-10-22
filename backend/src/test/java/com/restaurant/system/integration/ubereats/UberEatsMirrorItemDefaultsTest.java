package com.restaurant.system.integration.ubereats;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.integration.ubereats.config.UberEatsMirrorItemDefaults;
import com.restaurant.system.integration.ubereats.entity.UberEatsStoreMapping;
import java.util.*;
import org.junit.jupiter.api.Test;

class UberEatsMirrorItemDefaultsTest {
    final ObjectMapper json = new ObjectMapper();
    Map<String,Object> entry() {
        return new LinkedHashMap<>(Map.of("environment","sandbox","localStoreId",11L,
                "organizationId",7L,"uberStoreId","00000000-0000-4000-8000-000000000011",
                "uberItemId","stable-combo-root","localSku","vegetable_noodle",
                "optionGroup","SOUP_BASE","optionCode","soup_vegan"));
    }
    UberEatsMirrorItemDefaults configured(Object value) throws Exception {
        var config=new UberEatsMirrorItemDefaults();config.configuration=json.writeValueAsString(value);return config;
    }
    UberEatsStoreMapping store() {
        var store=new UberEatsStoreMapping();store.environment="sandbox";store.storeId=11L;
        store.organizationId=7L;store.uberStoreId="00000000-0000-4000-8000-000000000011";return store;
    }
    @Test void emptyDefaultHasNoEffect() {
        var config=new UberEatsMirrorItemDefaults();config.validate();
        assertThat(config.find(store(),"stable-combo-root","vegetable_noodle")).isEmpty();
    }
    @Test void fullScopeMustMatchAllSixDimensions() throws Exception {
        var config=configured(List.of(entry()));config.validate();
        assertThat(config.find(store(),"stable-combo-root","vegetable_noodle")).isPresent();
        for(String field:List.of("environment","localStoreId","organizationId","uberStoreId","uberItemId","localSku")) {
            var changed=entry();changed.put(field,switch(field) {
                case "localStoreId","organizationId" -> 99L;
                case "uberStoreId" -> "00000000-0000-4000-8000-000000000099";
                case "environment" -> "production";
                default -> "different-identity";
            });
            assertThat(configured(List.of(changed)).find(store(),"stable-combo-root","vegetable_noodle")).as(field).isEmpty();
        }
    }
    @Test void invalidStructureUnknownFieldsAndDuplicateKeysFailClosedWithoutEchoingInput() throws Exception {
        for(String value:List.of("", "{}", "null", "[null]", "[]{}", "[{\"environment\":\"sandbox\",\"environment\":\"production\"}]")) {
            var config=new UberEatsMirrorItemDefaults();config.configuration=value;
            assertThatThrownBy(config::validate).isInstanceOf(IllegalStateException.class).hasMessageContaining("UBER_EATS_MIRROR_ITEM_DEFAULTS");
        }
        var extra=entry();extra.put("private-untrusted-field","never-echo-this-private-value");
        var config=configured(List.of(extra));
        assertThatThrownBy(config::validate).hasMessageNotContaining("never-echo-this-private-value");
        assertThatThrownBy(configured(List.of(entry(),entry()))::validate).isInstanceOf(IllegalStateException.class);
        var differentSku=entry();differentSku.put("localSku","another_sku");
        assertThatThrownBy(configured(List.of(entry(),differentSku))::validate).isInstanceOf(IllegalStateException.class);
    }
    @Test void nullMissingWrongTypesAndInvalidScopeOrGroupAreRejected() throws Exception {
        for(String field:entry().keySet()) {
            var missing=entry();missing.remove(field);
            assertThatThrownBy(configured(List.of(missing))::validate).as("missing "+field).isInstanceOf(IllegalStateException.class);
            var nulled=entry();nulled.put(field,null);
            assertThatThrownBy(configured(List.of(nulled))::validate).as("null "+field).isInstanceOf(IllegalStateException.class);
        }
        for(var invalid:List.of(Map.entry("environment","local"),Map.entry("localStoreId","11"),
                Map.entry("localStoreId",0L),Map.entry("organizationId",1.5),Map.entry("uberStoreId","invalid"),
                Map.entry("uberItemId"," root"),Map.entry("localSku",""),Map.entry("optionGroup","ADD_ON"),Map.entry("optionCode",""))) {
            var changed=entry();changed.put(invalid.getKey(),invalid.getValue());
            assertThatThrownBy(configured(List.of(changed))::validate).as(invalid.getKey()).isInstanceOf(IllegalStateException.class);
        }
    }
}
