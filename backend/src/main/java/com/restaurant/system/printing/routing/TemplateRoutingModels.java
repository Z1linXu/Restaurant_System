package com.restaurant.system.printing.routing;
import java.util.ArrayList;
import java.util.List;
import com.restaurant.system.order.dto.CreateOrderItemRequest;
public final class TemplateRoutingModels {
 private TemplateRoutingModels(){}
 public static class Configuration {
  public boolean enabled;
  public long revision;
  public List<Group> groups=new ArrayList<>();
  public List<ItemOverride> item_overrides=new ArrayList<>();
 }
 public static class Group {
  public String group_code;
  public String name;
  public boolean enabled=true;
  public int sort_order;
  public List<Long> category_ids=new ArrayList<>();
  public List<Long> item_ids=new ArrayList<>();
  public List<String> combo_component_codes=new ArrayList<>();
  public String logical_target;
  public Long printer_id;
  public String mock_failure;
 }
 public static class ItemOverride {public Long menu_item_id;public List<String> group_codes=new ArrayList<>();}
 public static class PreviewRequest {public List<CreateOrderItemRequest> items=new ArrayList<>();}
 public static class FrozenIntent {
  /** Missing historical values are MOCK-only, never permission to enter a physical queue. */
  public String execution_mode;
  public boolean diagnostic_only;
  public String group_code;
  public String group_name;
  public String logical_target;
  public Long printer_id;
  public String rendered_text;
  public String reason;
  public String error_code;
  public long menu_revision;
  public long routing_revision;
  public String mock_failure;
  public List<String> source_keys=new ArrayList<>();
 }
}
