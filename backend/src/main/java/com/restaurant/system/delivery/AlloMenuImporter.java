package com.restaurant.system.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.menu.entity.MenuCategory;
import com.restaurant.system.menu.entity.MenuItem;
import com.restaurant.system.menu.repository.MenuCategoryRepository;
import com.restaurant.system.menu.repository.MenuItemRepository;
import com.restaurant.system.menu.template.TemplateMenuModels.*;
import com.restaurant.system.menu.template.TemplateMenuService;
import com.restaurant.system.menu.service.MenuRevisionService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Bounded delivery importer. Customer source files are private inputs, never executable SQL or credentials. */
@Service
public class AlloMenuImporter {
    private final JdbcTemplate jdbc;
    private final MenuItemRepository items;
    private final MenuCategoryRepository categories;
    private final TemplateMenuService menus;
    private final MenuRevisionService revisions;
    private final ObjectMapper mapper;

    public AlloMenuImporter(JdbcTemplate jdbc, MenuItemRepository items, MenuCategoryRepository categories,
                           TemplateMenuService menus, MenuRevisionService revisions, ObjectMapper mapper) {
        this.jdbc=jdbc; this.items=items; this.categories=categories; this.menus=menus; this.revisions=revisions; this.mapper=mapper;
    }
    public record Result(int created, int updated, int unchanged, int ownerEditedPreserved, String menuVersion) {}

    @Transactional
    public Result apply(Long storeId, JsonNode source) {
        validate(source);
        revisions.lockStoresInOrder(List.of(storeId));
        if (jdbc.queryForObject("select count(*) from stores where id=? and code='ALLO_NOUILLES_TEST' and printing_mode in ('MOCK','DISABLED')", Integer.class, storeId)!=1)
            throw fail("IMPORT_TARGET_NOT_AUTHORIZED");
        long station=ensureStation(storeId);
        Map<String,Long> categoryIds=new LinkedHashMap<>();
        int categoryIndex=0;
        for (JsonNode category:source.path("categories")) {
            String code=text(category,"code");
            categoryIds.put(code,importCategory(storeId,category,categoryIndex++));
        }
        List<JsonNode> sourceItems=new ArrayList<>();source.path("items").forEach(sourceItems::add);
        sourceItems.add(refill("REFILL_NOODLES","NOODLE","续面","Noodle refill","Nouilles supplémentaires"));
        sourceItems.add(refill("REFILL_RICE","RICE","续饭","Rice refill","Riz supplémentaire"));
        int created=0,updated=0,unchanged=0,preserved=0;
        for (int sourceIndex=0;sourceIndex<sourceItems.size();sourceIndex++) {
            JsonNode input=sourceItems.get(sourceIndex);
            String sourceKey="item:"+text(input,"code");String sku="AN_"+text(input,"code");
            Groups desired=groupsFor(input,source);
            String sourceHash=hash(List.of(input,desired,sourceIndex));
            var ledger=jdbc.queryForList("select entity_id,source_hash,applied_hash from merchant_menu_import_entries where store_id=? and source_key=?",storeId,sourceKey);
            MenuItem row;
            boolean creating=ledger.isEmpty();
            if (creating) {
                if(items.findAllByStoreIdOrderByIdAsc(storeId).stream().anyMatch(item->sku.equals(item.sku)))throw fail("IMPORT_UNTRACKED_SKU_CONFLICT");
                row=new MenuItem();row.store_id=storeId;row.sku=sku;row.created_at=LocalDateTime.now();row.is_sold_out=false;
            } else {
                row=items.findById(((Number)ledger.get(0).get("entity_id")).longValue()).orElseThrow(()->fail("IMPORT_TRACKED_ITEM_MISSING"));
                if(!Objects.equals(row.store_id,storeId)||!sku.equals(row.sku))throw fail("IMPORT_TRACKED_ITEM_SCOPE");
                boolean ownerChanged=!hash(current(row)).equals(ledger.get(0).get("applied_hash"));
                boolean sourceChanged=!sourceHash.equals(ledger.get(0).get("source_hash"));
                if(ownerChanged) {
                    if(sourceChanged)throw fail("IMPORT_OWNER_EDIT_CONFLICT: "+input.path("code").asText());
                    preserved++;continue;
                }
                if(!sourceChanged){unchanged++;continue;}
                preserveOptionIds(desired,menus.getItem(storeId,row.id));
            }
            Long category=categoryIds.get(text(input,"category_code"));if(category==null)throw fail("IMPORT_CATEGORY_REFERENCE");
            row.category_id=category;row.station_id=station;
            row.name_zh=text(input,"name_zh");row.name_en=text(input,"name_en");row.name_fr=text(input,"name_fr");
            row.refill_category_code=input.path("refill_category_code").isTextual()?input.path("refill_category_code").asText():null;
            row.item_type="DRINK".equals(text(input,"category_code"))?"DRINK":"FOOD";row.base_price=money(input,"base_price");row.is_active=true;row.sort_order=sourceIndex;row.updated_at=LocalDateTime.now();
            row=items.saveAndFlush(row);menus.saveItem(storeId,row.id,desired);items.flush();
            String appliedHash=hash(current(row));
            if(creating)jdbc.update("insert into merchant_menu_import_entries(store_id,source_key,entity_kind,entity_id,source_hash,applied_hash) values(?,?,'ITEM',?,?,?)",storeId,sourceKey,row.id,sourceHash,appliedHash);
            else jdbc.update("update merchant_menu_import_entries set source_hash=?,applied_hash=?,imported_at=current_timestamp where store_id=? and source_key=?",sourceHash,appliedHash,storeId,sourceKey);
            if(creating)created++;else updated++;
        }
        return new Result(created,updated,unchanged,preserved,source.path("menu_version").asText());
    }
    private long importCategory(Long storeId,JsonNode input,int index){
        String code=text(input,"code"),key="category:"+code,sourceHash=hash(List.of(input,index));
        var ledger=jdbc.queryForList("select entity_id,source_hash,applied_hash from merchant_menu_import_entries where store_id=? and source_key=?",storeId,key);
        MenuCategory row;
        boolean creating=ledger.isEmpty();
        if(creating){
            if(!categories.findAllByStoreIdAndCode(storeId,code).isEmpty())throw fail("IMPORT_UNTRACKED_CATEGORY_CONFLICT");
            row=new MenuCategory();row.store_id=storeId;row.code=code;row.created_at=LocalDateTime.now();
        }else{
            row=categories.findById(((Number)ledger.get(0).get("entity_id")).longValue()).orElseThrow(()->fail("IMPORT_TRACKED_CATEGORY_MISSING"));
            if(!Objects.equals(row.store_id,storeId)||!code.equals(row.code))throw fail("IMPORT_TRACKED_CATEGORY_SCOPE");
            boolean sourceChanged=!sourceHash.equals(ledger.get(0).get("source_hash"));
            boolean ownerChanged=!hash(categoryState(row)).equals(ledger.get(0).get("applied_hash"));
            if(ownerChanged&&sourceChanged)throw fail("IMPORT_OWNER_CATEGORY_CONFLICT");
            if(ownerChanged||!sourceChanged)return row.id;
        }
        row.name_zh=text(input,"name_zh");row.name_en=text(input,"name_en");row.name_fr=text(input,"name_fr");row.sort_order=index;row.is_active=true;row.updated_at=LocalDateTime.now();
        row=categories.saveAndFlush(row);String applied=hash(categoryState(row));
        if(creating)jdbc.update("insert into merchant_menu_import_entries(store_id,source_key,entity_kind,entity_id,source_hash,applied_hash) values(?,?,'CATEGORY',?,?,?)",storeId,key,row.id,sourceHash,applied);
        else jdbc.update("update merchant_menu_import_entries set source_hash=?,applied_hash=?,imported_at=current_timestamp where store_id=? and source_key=?",sourceHash,applied,storeId,key);
        revisions.incrementRevision(storeId);
        return row.id;
    }
    private Object categoryState(MenuCategory row){return Arrays.asList(row.code,row.name_zh,row.name_en,row.name_fr,row.sort_order,row.is_active);}
    private Object current(MenuItem row) {
        Map<String,Object> state=new LinkedHashMap<>();
        state.put("type",row.item_type);state.put("category",row.category_id);state.put("station",row.station_id);state.put("sku",row.sku);
        state.put("zh",row.name_zh);state.put("en",row.name_en);state.put("fr",row.name_fr);state.put("price",row.base_price.stripTrailingZeros().toPlainString());
        state.put("active",row.is_active);state.put("soldout",row.is_sold_out);state.put("sort",row.sort_order);state.put("refill",row.refill_category_code);
        state.put("groups",menus.getItem(row.store_id,row.id).groups);return state;
    }
    private void preserveOptionIds(Groups desired,Groups before){
        for(Group group:desired.groups)for(Group old:before.groups)if(group.group_instance_code.equals(old.group_instance_code))
            for(Option option:group.options)for(Option previous:old.options)if(Objects.equals(option.option_code,previous.option_code))option.id=previous.id;
    }
    private Groups groupsFor(JsonNode item,JsonNode source){
        Groups result=new Groups();
        for(JsonNode code:item.path("option_group_codes"))for(JsonNode definition:source.path("option_groups"))if(code.asText().equals(definition.path("code").asText())){
            Group group=new Group();group.group_instance_code=code.asText().toLowerCase(Locale.ROOT);group.kind="CUSTOM";
            group.name=code.asText().equals("NOODLE_TYPE")?"面型":"双拼选择";
            group.name_translations=code.asText().equals("NOODLE_TYPE")?Map.of("zh-CN","面型","en","Noodle choice","fr-CA","Choix de nouilles"):Map.of("zh-CN","双拼选择","en","Choose two proteins","fr-CA","Choisissez deux grillades");
            group.selection_mode=definition.path("selection_mode").asText();group.required=true;
            group.min_select=definition.path("min_select").asInt(1);group.max_select=definition.path("max_select").asInt(1);
            JsonNode choices=code.asText().equals("NOODLE_TYPE")?source.path("noodle_options"):definition.path("options");
            for(JsonNode choice:choices){Option option=option(choice,"choice_"+choice.path("code").asText().toLowerCase(Locale.ROOT),"price_delta");
                option.sort_order=group.options.size();option.is_default=choice.path("code").asText().equals(definition.path("default_option_code").asText());group.options.add(option);}
            group.sort_order=result.groups.size();result.groups.add(group);
        }
        for(JsonNode code:item.path("addon_group_codes")){
            Group group=new Group();group.group_instance_code="paid_addons";group.name="加料";group.name_translations=Map.of("zh-CN","加料","en","Add-ons","fr-CA","Suppléments");
            group.kind="ADD_ON";group.selection_mode="MULTIPLE";group.required=false;group.sort_order=result.groups.size();
            for(JsonNode addon:source.path("addons"))if(code.asText().equals(addon.path("addon_group_code").asText())){
                Option option=option(addon,text(addon,"code"),"price");option.sort_order=group.options.size();group.options.add(option);
            }
            result.groups.add(group);
        }
        return result;
    }
    private Option option(JsonNode source,String code,String priceField){
        Option option=new Option();option.option_code=code;option.name_zh=text(source,"name_zh");option.name_en=text(source,"name_en");option.name_fr=text(source,"name_fr");
        option.price_delta=money(source,priceField);option.is_active=true;return option;
    }
    private JsonNode refill(String code,String category,String zh,String en,String fr){
        var row=mapper.createObjectNode();row.put("code",code);row.put("category_code",category);row.put("refill_category_code",category);row.put("name_zh",zh);row.put("name_en",en);row.put("name_fr",fr);row.put("base_price","0.00");return row;
    }
    private long ensureStation(Long store){
        var existing=jdbc.queryForList("select id from stations where store_id=? and code='BACK_KITCHEN'",Long.class,store);
        if(existing.size()>1)throw fail("IMPORT_STATION_DUPLICATE");if(!existing.isEmpty())return existing.get(0);
        return jdbc.queryForObject("insert into stations(store_id,code,name,name_zh,name_en,station_type,sort_order,is_active,created_at,updated_at) values(?,'BACK_KITCHEN','BACK_KITCHEN','后厨','Kitchen','KITCHEN',0,true,current_timestamp,current_timestamp) returning id",Long.class,store);
    }
    public static void validate(JsonNode source){
        if(source==null||!"ALLO_NOUILLES_SOURCE_MENU_V1".equals(source.path("schema_version").asText())||!"CAD".equals(source.path("currency").asText())||source.path("items").size()!=32||source.path("categories").size()!=5||source.path("addons").size()!=13)
            throw fail("IMPORT_SOURCE_INVALID");
        Set<String> codes=new HashSet<>(),categoryCodes=new HashSet<>();
        for(JsonNode category:source.path("categories")){if(!categoryCodes.add(text(category,"code")))throw fail("IMPORT_DUPLICATE_CODE");names(category);}
        for(JsonNode item:source.path("items")){
            if(!codes.add(text(item,"code"))||!categoryCodes.contains(text(item,"category_code")))throw fail("IMPORT_REFERENCE_INVALID");
            names(item);if(money(item,"base_price").signum()<=0)throw fail("IMPORT_PRICE_INVALID");
        }
        for(JsonNode addon:source.path("addons")){names(addon);if(money(addon,"price").signum()<=0)throw fail("IMPORT_PRICE_INVALID");}
        Set<String> optionGroups=new HashSet<>(),addonGroups=new HashSet<>(),addonCodes=new HashSet<>();
        for(JsonNode group:source.path("option_groups")){
            String groupCode=text(group,"code");if(!optionGroups.add(groupCode))throw fail("IMPORT_DUPLICATE_CODE");
            JsonNode choices="NOODLE_TYPE".equals(groupCode)?source.path("noodle_options"):group.path("options");
            if(!choices.isArray()||choices.isEmpty())throw fail("IMPORT_OPTION_REFERENCE_INVALID");
            Set<String> choiceCodes=new HashSet<>();
            for(JsonNode choice:choices){if(!choiceCodes.add(text(choice,"code")))throw fail("IMPORT_DUPLICATE_CODE");names(choice);money(choice,"price_delta");}
            if(group.hasNonNull("default_option_code")&&!choiceCodes.contains(text(group,"default_option_code")))throw fail("IMPORT_OPTION_REFERENCE_INVALID");
            validateReferences(group.path("option_codes"),choiceCodes);
            validateReferences(group.path("item_codes"),codes);
        }
        for(JsonNode group:source.path("addon_groups"))if(!addonGroups.add(text(group,"code")))throw fail("IMPORT_DUPLICATE_CODE");
        for(JsonNode addon:source.path("addons")){
            if(!addonCodes.add(text(addon,"code")))throw fail("IMPORT_DUPLICATE_CODE");
            if(!addonGroups.contains(text(addon,"addon_group_code")))throw fail("IMPORT_ADDON_REFERENCE_INVALID");
        }
        for(JsonNode group:source.path("addon_groups")){
            if(!group.path("addon_codes").isArray()||group.path("addon_codes").isEmpty())throw fail("IMPORT_ADDON_REFERENCE_INVALID");
            validateReferences(group.path("addon_codes"),addonCodes);
            for(JsonNode code:group.path("addon_codes"))if(!java.util.stream.StreamSupport.stream(source.path("addons").spliterator(),false)
                .anyMatch(addon->code.asText().equals(addon.path("code").asText())&&group.path("code").asText().equals(addon.path("addon_group_code").asText())))throw fail("IMPORT_ADDON_REFERENCE_INVALID");
        }
        for(JsonNode item:source.path("items")){
            validateReferences(item.path("option_group_codes"),optionGroups);
            validateReferences(item.path("addon_group_codes"),addonGroups);
        }
        if(!source.path("combo_offers").isArray()||!source.path("combo_offers").isEmpty())throw fail("IMPORT_UNCONFIRMED_COMBO");
        if(!"SAME_CATEGORY_ONLY".equals(source.path("free_refills").path("cross_eligibility").asText()))throw fail("IMPORT_REFILL_RULE_UNCONFIRMED");
    }
    private static void validateReferences(JsonNode values,Set<String> allowed){
        if(values.isMissingNode())return;
        if(!values.isArray())throw fail("IMPORT_REFERENCE_INVALID");
        Set<String> seen=new HashSet<>();for(JsonNode value:values)if(!value.isTextual()||!allowed.contains(value.asText())||!seen.add(value.asText()))throw fail("IMPORT_REFERENCE_INVALID");
    }
    private static void names(JsonNode row){for(String key:List.of("name_zh","name_en","name_fr")){String value=text(row,key);if(value.length()>255||value.chars().anyMatch(c->c<32))throw fail("IMPORT_NAME_INVALID");}}
    private static BigDecimal money(JsonNode row,String key){try{if(!row.path(key).isTextual())throw fail("IMPORT_MONEY_STRING_REQUIRED");BigDecimal value=new BigDecimal(row.path(key).asText());if(value.signum()<0||value.scale()>2)throw fail("IMPORT_PRICE_INVALID");return value.setScale(2);}catch(NumberFormatException ex){throw fail("IMPORT_PRICE_INVALID");}}
    private static String text(JsonNode row,String key){String value=row.path(key).asText("").trim();if(value.isEmpty())throw fail("IMPORT_REQUIRED_FIELD: "+key);return value;}
    private String hash(Object value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsString(canonical(mapper.valueToTree(value))).getBytes(StandardCharsets.UTF_8)));}catch(Exception ex){throw fail("IMPORT_FINGERPRINT_FAILED");}}
    private JsonNode canonical(JsonNode value){
        if(value.isObject()){
            var sorted=mapper.createObjectNode();var keys=new TreeSet<String>();value.fieldNames().forEachRemaining(keys::add);
            for(String key:keys)sorted.set(key,canonical(value.get(key)));return sorted;
        }
        if(value.isArray()){var ordered=mapper.createArrayNode();for(JsonNode child:value)ordered.add(canonical(child));return ordered;}
        return value;
    }
    private static BusinessException fail(String code){return new BusinessException(code);}
}
