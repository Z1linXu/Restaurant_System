package com.restaurant.system.menu.template;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
public interface TemplateItemOptionGroupRepository extends JpaRepository<TemplateItemOptionGroup,Long> {
    @Query("select g from TemplateItemOptionGroup g where g.menu_item_id=:itemId order by g.sort_order,g.id")
    List<TemplateItemOptionGroup> forItem(Long itemId);
}
