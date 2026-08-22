package com.restaurant.system.platform.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.restaurant.system.menu.entity.MenuCategory;
import com.restaurant.system.menu.entity.MenuItem;
import com.restaurant.system.menu.entity.MenuItemOption;
import com.restaurant.system.menu.repository.MenuCategoryRepository;
import com.restaurant.system.menu.repository.MenuItemOptionRepository;
import com.restaurant.system.menu.repository.MenuItemRepository;
import com.restaurant.system.station.entity.Station;
import com.restaurant.system.station.repository.StationRepository;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.entity.User;
import com.restaurant.system.user.repository.StoreRepository;
import com.restaurant.system.user.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ContextConfiguration(classes = PlatformAdminStoreScopedRepositoryTest.JpaSliceConfiguration.class)
class PlatformAdminStoreScopedRepositoryTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = MybatisPlusAutoConfiguration.class)
    @EntityScan(basePackages = "com.restaurant.system")
    @EnableJpaRepositories(basePackages = "com.restaurant.system")
    static class JpaSliceConfiguration {
    }

    @Autowired private StoreRepository storeRepository;
    @Autowired private StationRepository stationRepository;
    @Autowired private MenuCategoryRepository menuCategoryRepository;
    @Autowired private MenuItemRepository menuItemRepository;
    @Autowired private MenuItemOptionRepository menuItemOptionRepository;
    @Autowired private UserRepository userRepository;

    @Test
    void storeScopedReadsExcludeRowsOwnedByAnotherStore() {
        Store firstStore = store("FIRST");
        Store secondStore = store("SECOND");
        storeRepository.saveAll(List.of(firstStore, secondStore));

        Station firstStation = new Station();
        firstStation.store_id = firstStore.id;
        firstStation.code = "FIRST_STATION";
        Station secondStation = new Station();
        secondStation.store_id = secondStore.id;
        secondStation.code = "SECOND_STATION";
        stationRepository.saveAll(List.of(firstStation, secondStation));

        MenuCategory firstCategory = new MenuCategory();
        firstCategory.store_id = firstStore.id;
        firstCategory.code = "FIRST_CATEGORY";
        MenuCategory secondCategory = new MenuCategory();
        secondCategory.store_id = secondStore.id;
        secondCategory.code = "SECOND_CATEGORY";
        menuCategoryRepository.saveAll(List.of(firstCategory, secondCategory));

        MenuItem firstItem = new MenuItem();
        firstItem.store_id = firstStore.id;
        firstItem.category_id = firstCategory.id;
        firstItem.name_zh = "第一门店菜品";
        MenuItem secondItem = new MenuItem();
        secondItem.store_id = secondStore.id;
        secondItem.category_id = secondCategory.id;
        secondItem.name_zh = "第二门店菜品";
        menuItemRepository.saveAll(List.of(firstItem, secondItem));

        MenuItemOption firstOption = new MenuItemOption();
        firstOption.menu_item_id = firstItem.id;
        firstOption.name_zh = "第一门店选项";
        MenuItemOption secondOption = new MenuItemOption();
        secondOption.menu_item_id = secondItem.id;
        secondOption.name_zh = "第二门店选项";
        menuItemOptionRepository.saveAll(List.of(firstOption, secondOption));

        User firstUser = new User();
        firstUser.setStore_id(firstStore.id);
        firstUser.setUsername("first-user");
        User secondUser = new User();
        secondUser.setStore_id(secondStore.id);
        secondUser.setUsername("second-user");
        userRepository.saveAll(List.of(firstUser, secondUser));

        assertThat(stationRepository.findAllByStoreIdOrderByIdAsc(firstStore.id)).containsExactly(firstStation);
        assertThat(menuCategoryRepository.findAllByStoreIdOrderByIdAsc(firstStore.id)).containsExactly(firstCategory);
        assertThat(menuItemRepository.findAllByStoreIdOrderByIdAsc(firstStore.id)).containsExactly(firstItem);
        assertThat(menuItemOptionRepository.findAllByStoreIdOrderByIdAsc(firstStore.id)).containsExactly(firstOption);
        assertThat(userRepository.findAllByStore_id(firstStore.id)).containsExactly(firstUser);
    }

    private Store store(String code) {
        Store store = new Store();
        store.code = code;
        store.name = code;
        store.status = "active";
        return store;
    }
}
