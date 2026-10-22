package com.restaurant.system.integration.ubereats;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.integration.ubereats.config.UberEatsMirrorItemDefaults;
import com.restaurant.system.integration.ubereats.dto.UberOrderSnapshot;
import com.restaurant.system.integration.ubereats.entity.*;
import com.restaurant.system.integration.ubereats.mapping.UberEatsMenuMappingService;
import com.restaurant.system.integration.ubereats.repository.UberEatsMenuMappingRepository;
import com.restaurant.system.menu.dto.*;
import com.restaurant.system.menu.service.MenuService;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UberEatsMirrorItemMappingTest {
    final UberEatsMirrorItemDefaults defaults = new UberEatsMirrorItemDefaults();
    final MenuService menus = mock(MenuService.class);
    final UberEatsMenuMappingRepository mappings = mock(UberEatsMenuMappingRepository.class);
    final UberEatsMenuMappingService service = new UberEatsMenuMappingService(menus,mappings,defaults);
    UberEatsStoreMapping store;
    UberEatsMenuMapping rootRule;
    MenuCatalogResponse.ItemResponse item;
    Map<String,Object> scope;
    @BeforeEach void fixture() throws Exception {
        store=new UberEatsStoreMapping();store.id=1L;store.storeId=11L;store.organizationId=7L;
        store.environment="sandbox";store.uberStoreId="00000000-0000-4000-8000-000000000011";
        store.processingMode="KITCHEN_MIRROR";
        scope=new LinkedHashMap<>(Map.of("environment","sandbox","localStoreId",11L,"organizationId",7L,
                "uberStoreId",store.uberStoreId,"uberItemId","stable-combo-root","localSku","vegetable_noodle",
                "optionGroup","SOUP_BASE","optionCode","soup_vegan"));configure();
        item=new MenuCatalogResponse.ItemResponse(17L,2L,3L,"素面","Vegetable noodle","vegetable_noodle","food",BigDecimal.TEN,true,false,1,
                new ArrayList<>(List.of(option(1L,"combo","COMBO","addon","套餐"),option(2L,"soup_vegan","SOUP_BASE","soup_base","素汤"),
                        option(3L,"soup_beef","SOUP_BASE","soup_base","肉汤"),option(4L,"size_regular","SIZE","size","中碗"))));
        var combo=new StoreComboConfigurationResponse();
        combo.groups.add(group("COMBO_EGG","combo_tea_egg","卤蛋"));
        combo.groups.add(group("COMBO_SIDE","combo_cucumber_salad","黄瓜"));
        var catalog=new MenuCatalogResponse(store.storeId,store.organizationId,1L,null,"fixture","fixture",null,null,combo,
                List.of(new MenuCatalogResponse.CategoryResponse(2L,"SOUP_NOODLE","汤面","Noodles",1,true,List.of(item))));
        when(menus.getCatalog(store.storeId)).thenReturn(catalog);
        rootRule=new UberEatsMenuMapping();rootRule.kind="ITEM";rootRule.identifierType="ID";
        rootRule.uberIdentifier="stable-combo-root";rootRule.uberItemId="";rootRule.localMenuItemId=item.id;rootRule.itemMappingMode="COMBO_ROOT";
        when(mappings.findAllByStoreMappingIdOrderByIdAsc(store.id)).thenReturn(List.of(rootRule));
    }
    void configure() throws Exception { defaults.configuration=new ObjectMapper().writeValueAsString(List.of(scope));defaults.validate(); }
    MenuCatalogResponse.OptionResponse option(Long id,String code,String group,String type,String zh) {
        return new MenuCatalogResponse.OptionResponse(id,type,code,group,null,1,zh,code,BigDecimal.ZERO,true);
    }
    StoreComboConfigurationResponse.GroupResponse group(String code,String componentCode,String zh) {
        var group=new StoreComboConfigurationResponse.GroupResponse();group.group_code=code;group.component_group=code;
        group.enabled=true;group.required=true;group.selection_rule="EXACTLY_ONE";
        var component=new StoreComboConfigurationResponse.ComponentResponse();component.component_group=code;component.component_code=componentCode;
        component.name_zh=zh;component.name_en=componentCode;component.enabled=true;group.components.add(component);return group;
    }
    UberOrderSnapshot.Item modifier(String code,int quantity) {
        return new UberOrderSnapshot.Item("id-"+code,code,code,quantity,false,"",List.of(),List.of());
    }
    List<UberOrderSnapshot.Item> selected() {return new ArrayList<>(List.of(modifier("size_regular",1),modifier("combo_tea_egg",1),modifier("combo_cucumber_salad",1)));}
    UberOrderSnapshot snapshot(List<UberOrderSnapshot.Item> mods) {
        var root=new UberOrderSnapshot.Item("stable-combo-root","","Combo",1,false,"",mods,List.of());
        return new UberOrderSnapshot("fixture-order",store.uberStoreId,"FIX","ACCEPTED","","","DELIVERY_BY_UBER","","","",List.of(root),List.of());
    }
    UberEatsMenuMappingService.Result map(List<UberOrderSnapshot.Item> mods) {return service.mapMirror(store,snapshot(mods));}
    @Test void absentSoupInjectsCanonicalSnapshotOnceAndRepeatedPreviewIsIdempotent() {
        var source=selected();
        for(int n=0;n<2;n++) {
            var result=map(source);assertThat(result.valid()).isTrue();assertThat(result.warnings()).isEmpty();
            assertThat(result.request().items.get(0).options).extracting(o->o.option_code_snapshot)
                    .containsExactlyInAnyOrder("combo","size_regular","combo_tea_egg","combo_cucumber_salad","soup_vegan");
            assertThat(result.request().items.get(0).options).filteredOn(o->"soup_vegan".equals(o.option_code_snapshot)).singleElement().satisfies(o->{
                assertThat(o.option_id).isEqualTo(2L);assertThat(o.quantity).isEqualTo(1);
                assertThat(o.option_group_snapshot).isEqualTo("SOUP_BASE");assertThat(o.option_type_snapshot).isEqualTo("soup_base");
                assertThat(o.option_name_snapshot_zh).isEqualTo("素汤");});
        }
        assertThat(source).hasSize(3);
    }
    @Test void explicitSameSoupIsRetainedWithoutDuplication() {
        var mods=selected();mods.add(modifier("soup_vegan",1));var result=map(mods);
        assertThat(result.valid()).isTrue();assertThat(result.warnings()).isEmpty();
        assertThat(result.request().items.get(0).options).filteredOn(o->"SOUP_BASE".equals(o.option_group_snapshot)).hasSize(1);
    }
    @Test void explicitMeatMultipleAndInvalidQuantityAreBlockingErrors() {
        for(var additions:List.of(List.of(modifier("soup_beef",1)),List.of(modifier("soup_vegan",2)),
                List.of(modifier("soup_vegan",1),modifier("soup_beef",1)))) {
            var mods=selected();mods.addAll(additions);var result=map(mods);
            assertThat(result.valid()).isFalse();assertThat(result.errors()).anyMatch(e->e.contains("SOUP_BASE_DEFAULT_SELECTION_CONFLICT"));
        }
    }
    @Test void unknownModifierBlocksDefaultRatherThanSilentlyChoosingSoup() {
        var mods=selected();mods.add(modifier("unmapped-new-soup",1));var result=map(mods);
        assertThat(result.valid()).isFalse();assertThat(result.errors()).anyMatch(e->e.contains("SOUP_BASE_DEFAULT_SOURCE_REVIEW_REQUIRED"));
    }
    @Test void missingOrAmbiguousConfiguredChoiceBlocks() {
        var vegan=item.options.remove(1);assertThat(map(selected()).errors()).anyMatch(e->e.contains("SOUP_BASE_DEFAULT_CHOICE_UNRESOLVED"));
        item.options.add(vegan);item.options.add(option(9L,"soup_vegan","SOUP_BASE","soup_base","另一个素汤"));
        assertThat(map(selected()).errors()).anyMatch(e->e.contains("SOUP_BASE_DEFAULT_CHOICE_UNRESOLVED"));
    }
    @Test void otherSelectionErrorsStayFatalAndDoNotClearImplicitSoupOrCombo() {
        var mods=selected();mods.remove(0);var result=map(mods);
        assertThat(result.valid()).isFalse();assertThat(result.errors()).anyMatch(e->e.contains("REQUIRED_SIZE_MISSING"));
        assertThat(result.warnings()).isEmpty();assertThat(result.request().items.get(0).options)
                .extracting(o->o.option_code_snapshot).contains("combo","soup_vegan");
    }
    @Test void otherStoreOrEnvironmentKeepsPreviousMirrorBehaviorAndOrderManagerNeverUsesDefault() throws Exception {
        for(String field:List.of("environment","localStoreId","organizationId","uberStoreId","uberItemId")) {
            Object old=scope.get(field);scope.put(field,switch(field) {
                case "localStoreId","organizationId" -> 99L;
                case "uberStoreId" -> "00000000-0000-4000-8000-000000000099";
                case "environment" -> "production";
                default -> "different-identity";
            });configure();var result=map(selected());
            assertThat(result.valid()).as(field).isTrue();assertThat(result.warnings()).anyMatch(w->w.contains("REQUIRED_SOUP_BASE_MISSING"));
            assertThat(result.request().items.get(0).options).noneMatch(o->"soup_vegan".equals(o.option_code_snapshot));scope.put(field,old);
        }
        configure();assertThat(service.map(store,snapshot(selected())).errors()).anyMatch(e->e.contains("REQUIRED_SOUP_BASE_MISSING"));
    }
    @Test void configuredRootSkuMappingModeAndIdentityDriftCannotBecomeOrdinaryReleasableMirror() {
        item.sku="different_sku";assertThat(map(selected()).errors()).anyMatch(e->e.contains("SOUP_BASE_DEFAULT_LOCAL_IDENTITY_DRIFT"));
        item.sku="vegetable_noodle";rootRule.itemMappingMode="STANDARD";
        assertThat(map(selected()).errors()).anyMatch(e->e.contains("SOUP_BASE_DEFAULT_ROOT_MAPPING_DRIFT"));
        rootRule.itemMappingMode="COMBO_ROOT";rootRule.uberIdentifier="different-root";
        assertThat(map(selected()).errors()).anyMatch(e->e.contains("SOUP_BASE_DEFAULT_ROOT_MAPPING_DRIFT"));
        rootRule.uberIdentifier="stable-combo-root";item.is_active=false;
        assertThat(map(selected()).errors()).anyMatch(e->e.contains("SOUP_BASE_DEFAULT_LOCAL_IDENTITY_DRIFT"));
    }
}
