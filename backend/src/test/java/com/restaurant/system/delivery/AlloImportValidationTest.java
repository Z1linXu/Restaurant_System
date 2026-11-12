package com.restaurant.system.delivery;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Synthetic data only. Tests the external-source boundary before any write. */
class AlloImportValidationTest {
 ObjectMapper mapper=new ObjectMapper();
 ObjectNode row(String code){var r=mapper.createObjectNode();r.put("code",code);r.put("name_zh","合成");r.put("name_en","Synthetic");r.put("name_fr","Synthétique");return r;}
 ObjectNode source(){var s=mapper.createObjectNode();s.put("schema_version","ALLO_NOUILLES_SOURCE_MENU_V1");s.put("currency","CAD");
 var cats=s.putArray("categories");for(int i=0;i<5;i++)cats.add(row("CAT"+i));
 var items=s.putArray("items");for(int i=0;i<32;i++){var item=row("ITEM"+i);item.put("category_code","CAT0");item.put("base_price","1.00");items.add(item);}
 var addons=s.putArray("addons");var group=row("PAID");var refs=group.putArray("addon_codes");
 for(int i=0;i<13;i++){var addon=row("ADD"+i);addon.put("addon_group_code","PAID");addon.put("price","1.00");addons.add(addon);refs.add("ADD"+i);}
 s.putArray("addon_groups").add(group);var choice=row("ONE");choice.put("price_delta","0.00");var def=row("CHOICE");def.putArray("options").add(choice);s.putArray("option_groups").add(def);
 s.putArray("combo_offers");s.putObject("free_refills").put("cross_eligibility","SAME_CATEGORY_ONLY");return s;}
 @Test void validSyntheticSourceHasNoWriteDependency(){assertDoesNotThrow(()->AlloMenuImporter.validate(source()));}
 @Test void canonicalAssignmentDtoFieldsDoNotChangeExistingImportFingerprints() throws Exception {
  var importer=new AlloMenuImporter(null,null,null,null,null,mapper);
  var option=new com.restaurant.system.menu.template.TemplateMenuModels.Option();option.option_code="extra_beef";option.option_group="ADD_ON";option.name_zh="牛肉";option.name_en="Beef";option.name_fr="Bœuf";
  var legacy=mapper.valueToTree(option);((ObjectNode)legacy).remove(java.util.List.of("store_addon_id","addon_eligible"));
  option.store_addon_id=123L;option.addon_eligible=true;
  var hash=AlloMenuImporter.class.getDeclaredMethod("hash",Object.class);hash.setAccessible(true);
  assertEquals(hash.invoke(importer,legacy),hash.invoke(importer,option));
 }
 @Test void unknownOptionGroupIsRejected(){var s=source();((ObjectNode)s.path("items").get(0)).putArray("option_group_codes").add("MISSING");assertThrows(RuntimeException.class,()->AlloMenuImporter.validate(s));}
 @Test void unknownAddonGroupIsRejected(){var s=source();((ObjectNode)s.path("items").get(0)).putArray("addon_group_codes").add("MISSING");assertThrows(RuntimeException.class,()->AlloMenuImporter.validate(s));}
 @Test void duplicateReferencesAreRejected(){var s=source();((ObjectNode)s.path("items").get(0)).putArray("option_group_codes").add("CHOICE").add("CHOICE");assertThrows(RuntimeException.class,()->AlloMenuImporter.validate(s));}
 @Test void emptyChoicesCannotSilentlyDropRequiredMeaning(){var s=source();((ObjectNode)s.path("option_groups").get(0)).putArray("options");assertThrows(RuntimeException.class,()->AlloMenuImporter.validate(s));}
 @Test void unknownAddonLeafIsRejected(){var s=source();((ObjectNode)s.path("addon_groups").get(0)).withArray("addon_codes").add("MISSING");assertThrows(RuntimeException.class,()->AlloMenuImporter.validate(s));}
 @Test void duplicateCategoryAndChoiceCodesAreRejected(){var s=source();((ObjectNode)s.path("categories").get(1)).put("code","CAT0");assertThrows(RuntimeException.class,()->AlloMenuImporter.validate(s));var t=source();((ObjectNode)t.path("option_groups").get(0)).withArray("options").add(row("ONE").put("price_delta","0.00"));assertThrows(RuntimeException.class,()->AlloMenuImporter.validate(t));}
}
