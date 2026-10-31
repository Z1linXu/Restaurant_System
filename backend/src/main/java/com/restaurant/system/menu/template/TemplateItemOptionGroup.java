package com.restaurant.system.menu.template;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name="template_item_option_groups")
public class TemplateItemOptionGroup {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @Column(name="store_id") public Long store_id;
    @Column(name="menu_item_id") public Long menu_item_id;
    @Column(name="group_code") public String group_code;
    public String name;
    public String kind;
    @Column(name="selection_mode") public String selection_mode;
    public boolean required;
    @Column(name="is_active") public boolean is_active;
    @Column(name="sort_order") public int sort_order;
    @Column(name="source_library_code") public String source_library_code;
}
