package com.restaurant.system.menu.template;
import jakarta.persistence.*;
import lombok.Data;
@Data @Entity @Table(name="template_store_configuration")
public class TemplateStoreConfiguration {
    @Id @Column(name="store_id") public Long store_id;
    @Column(name="library_json",columnDefinition="text") public String library_json = "{\"groups\":[]}";
    @Column(name="routing_json",columnDefinition="text") public String routing_json = "{\"enabled\":false,\"groups\":[],\"item_overrides\":[]}";
    @Column(name="routing_revision") public long routing_revision;
}
