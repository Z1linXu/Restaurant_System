package com.restaurant.system.delivery;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.menu.service.MenuRevisionService;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.repository.StoreRepository;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Store-owned display configuration; no credentials, endpoint, price or workflow state. */
@Service
public class StorePresentationService {
    public static final Set<String> LOCALES=Set.of("zh-CN","en","fr-CA");
    private final StoreRepository stores; private final MenuRevisionService revisions; private final ObjectMapper mapper; private final JdbcTemplate jdbc;
    public StorePresentationService(StoreRepository stores,MenuRevisionService revisions,ObjectMapper mapper,JdbcTemplate jdbc){this.stores=stores;this.revisions=revisions;this.mapper=mapper;this.jdbc=jdbc;}
    public Map<String,Object> read(Store store){try{return store.presentation_json==null?new LinkedHashMap<>():mapper.readValue(store.presentation_json,new TypeReference<LinkedHashMap<String,Object>>(){});}catch(Exception e){throw fail("PRESENTATION_CONFIGURATION_INVALID");}}
    public Map<String,Object> publicView(Store store){var all=read(store);all.remove("receipts");return all;}
    public Map<String,Object> publicView(Long id){return publicView(require(id));}
    public static class ReceiptSettings {
        public boolean enabled;
        public String default_language="zh-CN";
        public String kitchen_language;
        public String frontdesk_language;
        public List<String> kitchen_station_codes=new ArrayList<>();
        public long revision;
        public Long expected_revision;
        /** Category code -> GRAB enabled. Absent means retain the current/legacy policy. */
        public Map<String,Boolean> kitchen_category_enabled;
        public boolean kitchenEnabled(String code) {
            if (Set.of("DRINK", "ALCOHOL").contains(code == null ? "" : code)) return false;
            return code == null || kitchen_category_enabled == null || !Boolean.FALSE.equals(kitchen_category_enabled.get(code));
        }
    }
    public ReceiptSettings receipts(Store store){Object value=read(store).get("receipts");return value==null?new ReceiptSettings():mapper.convertValue(value,ReceiptSettings.class);}
    public ReceiptSettings receipts(Long id){return receipts(require(id));}
    @Transactional
    public Map<String,Object> save(Long id,Map<String,Object> input){
        revisions.lockStoresInOrder(List.of(id));Store store=require(id);
        if(input==null||!Set.of("display_name","logo_url","primary_color","default_locale","reporting_style","kitchen_configuration","notice","address","phone").containsAll(input.keySet()))throw fail("PRESENTATION_FIELD_INVALID");
        for(var e:input.entrySet()){
            if("notice".equals(e.getKey())){var texts=mapper.convertValue(e.getValue(),new TypeReference<Map<String,String>>(){});if(!LOCALES.containsAll(texts.keySet())||texts.values().stream().anyMatch(v->v==null||v.length()>800||v.chars().anyMatch(c->c<32)))throw fail("PRESENTATION_NOTICE_INVALID");continue;}
            if(!(e.getValue() instanceof String value)||value.length()>255||value.chars().anyMatch(c->c<32))throw fail("PRESENTATION_VALUE_INVALID");
            if("default_locale".equals(e.getKey())&&!LOCALES.contains(value))throw fail("LOCALE_INVALID");
            if("primary_color".equals(e.getKey())&&!value.matches("#[0-9a-fA-F]{6}"))throw fail("BRAND_COLOR_INVALID");
            if("logo_url".equals(e.getKey())&&!value.isEmpty()&&!value.equals("/merchant/logo.png"))throw fail("BRAND_LOGO_PATH_INVALID");
            if("reporting_style".equals(e.getKey())&&!Set.of("GENERIC","LEGACY").contains(value))throw fail("REPORTING_STYLE_INVALID");
            if("kitchen_configuration".equals(e.getKey())&&!Set.of("STATION","TEMPLATE_GROUPS").contains(value))throw fail("KITCHEN_CONFIGURATION_INVALID");
        }
        var config=read(store);config.putAll(input);write(store,config);return publicView(store);
    }
    @Transactional
    public ReceiptSettings saveReceipts(Long id,ReceiptSettings input){
        revisions.lockStoresInOrder(List.of(id));Store store=require(id);
        if(input==null||!LOCALES.contains(input.default_language)||input.kitchen_station_codes==null||input.kitchen_station_codes.size()>20)throw fail("RECEIPT_LANGUAGE_INVALID");
        for(String locale:Arrays.asList(input.kitchen_language,input.frontdesk_language))if(locale!=null&&!LOCALES.contains(locale))throw fail("RECEIPT_LANGUAGE_INVALID");
        if(new HashSet<>(input.kitchen_station_codes).size()!=input.kitchen_station_codes.size())throw fail("RECEIPT_STATION_INVALID");
        for(String code:input.kitchen_station_codes)if(code==null||!code.matches("[A-Z][A-Z0-9_]{0,63}")||jdbc.queryForObject("select count(*) from stations where store_id=? and code=? and is_active=true",Integer.class,id,code)!=1)throw fail("RECEIPT_STATION_INVALID");
        ReceiptSettings current=receipts(store);
        if(input.expected_revision!=null && input.expected_revision!=current.revision)throw new ReceiptSettingsConflict();
        if(input.kitchen_category_enabled==null)input.kitchen_category_enabled=current.kitchen_category_enabled;
        if(input.kitchen_category_enabled!=null) {
            if(input.kitchen_category_enabled.size()>100)throw fail("RECEIPT_CATEGORY_INVALID");
            for(var entry:input.kitchen_category_enabled.entrySet()) {
                String code=entry.getKey();
                if(code==null||!code.matches("[A-Z][A-Z0-9_]{0,63}")||entry.getValue()==null
                    ||jdbc.queryForObject("select count(*) from menu_categories where store_id=? and code=?",Integer.class,id,code)!=1)
                    throw fail("RECEIPT_CATEGORY_INVALID");
                if(Set.of("DRINK","ALCOHOL").contains(code)&&entry.getValue())throw fail("DIRECT_SERVE_CATEGORY_NOT_KITCHEN");
            }
            input.kitchen_category_enabled=new LinkedHashMap<>(input.kitchen_category_enabled);
        }
        boolean routingChanged=!Objects.equals(current.kitchen_category_enabled,input.kitchen_category_enabled);
        var config=read(store);input.revision=current.revision+1;input.expected_revision=null;
        config.put("receipts",input);write(store,config);
        if(routingChanged) { stores.flush();revisions.incrementRevision(id); }
        return input;
    }
    public static final class ReceiptSettingsConflict extends BusinessException {
        public ReceiptSettingsConflict(){super("RECEIPT_SETTINGS_STALE: reload before saving");}
    }
    private Store require(Long id){return stores.findById(id).orElseThrow(()->fail("STORE_NOT_FOUND"));}
    private void write(Store store,Map<String,Object> value){try{store.presentation_json=mapper.writeValueAsString(value);store.updated_at=LocalDateTime.now();stores.save(store);}catch(Exception e){throw fail("PRESENTATION_CONFIGURATION_INVALID");}}
    private static BusinessException fail(String code){return new BusinessException(code);}
}
