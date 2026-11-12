package com.restaurant.system.menu.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.restaurant.system.menu.addon.StoreAddonOptionResolver;
import com.restaurant.system.menu.entity.MenuItem;
import com.restaurant.system.menu.entity.MenuItemOption;
import com.restaurant.system.menu.remove.StoreRemoveOptionResolver;
import com.restaurant.system.menu.repository.MenuItemOptionRepository;
import com.restaurant.system.menu.repository.MenuItemRepository;
import com.restaurant.system.menu.service.MenuRevisionService;
import com.zaxxer.hikari.HikariDataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Transaction behavior proof; real OSIV/JPA HTTP concurrency is covered by QA acceptance. */
class OwnerMenuOptionReadTransactionTest {
    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Transactions {}

    @Test
    void concurrentReadsShareTheirReadOnlyTransactionConnectionWithCanonicalLookup() throws Exception {
        try (var pool = new HikariDataSource()) {
            pool.setJdbcUrl("jdbc:h2:mem:option_read_" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
            pool.setUsername("sa");
            pool.setPassword("");
            pool.setMaximumPoolSize(2);
            pool.setMinimumIdle(0);
            pool.setConnectionTimeout(1000);
            var dataSource = new CountingDataSource(pool);
            var jdbc = new JdbcTemplate(dataSource);
            jdbc.execute("create table organization_addon_definitions(id bigint,organization_id bigint,code varchar(64))");
            jdbc.execute("create table store_addons(id bigint,store_id bigint,organization_id bigint,organization_addon_definition_id bigint,name_zh varchar(255),name_en varchar(255),price numeric(38,2),active boolean)");
            jdbc.update("insert into organization_addon_definitions values(1,1,'extra_beef')");
            jdbc.update("insert into store_addons values(1,1,1,1,'加牛肉','Extra Beef',3.25,true)");

            var item = new MenuItem();
            item.id = 10L;
            item.store_id = 1L;
            var leaf = new MenuItemOption();
            leaf.id = 100L;
            leaf.menu_item_id = item.id;
            leaf.store_addon_id = 1L;
            leaf.store_addon_store_id = 1L;
            leaf.addon_eligible = true;
            leaf.name_zh = "Old copy";
            leaf.name_fr = "Bœuf supplémentaire";
            leaf.price_delta = new BigDecimal("9.99");
            var items = mock(MenuItemRepository.class);
            var options = mock(MenuItemOptionRepository.class);
            var bothReadsStarted = new CyclicBarrier(2);
            when(items.findById(item.id)).thenAnswer(call -> {
                assertReadOnlyTransaction();
                return Optional.of(item);
            });
            when(options.findAllByMenuItemIdOrdered(item.id)).thenAnswer(call -> {
                assertReadOnlyTransaction();
                bothReadsStarted.await(5, TimeUnit.SECONDS);
                return List.of(leaf);
            });
            var resolver = new StoreAddonOptionResolver(new NamedParameterJdbcTemplate(jdbc));
            try (var context = new AnnotationConfigApplicationContext()) {
                context.register(Transactions.class);
                context.registerBean(PlatformTransactionManager.class, () -> new DataSourceTransactionManager(dataSource));
                context.registerBean(StoreRemoveOptionResolver.class, () -> mock(StoreRemoveOptionResolver.class));
                context.registerBean(OwnerMenuOptionServiceImpl.class,
                    () -> new OwnerMenuOptionServiceImpl(resolver, items, options, mock(MenuRevisionService.class)));
                context.refresh();
                var service = context.getBean(OwnerMenuOptionServiceImpl.class);
                dataSource.borrowed.set(0);
                var executor = Executors.newFixedThreadPool(2);
                try {
                    var first = executor.submit(() -> service.getOptions(item.id));
                    var second = executor.submit(() -> service.getOptions(item.id));
                    for (var result : List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS))) {
                        assertThat(result).singleElement().satisfies(option -> {
                            assertThat(option.option_code).isEqualTo("extra_beef");
                            assertThat(option.name_zh).isEqualTo("加牛肉");
                            assertThat(option.name_fr).isEqualTo("Bœuf supplémentaire");
                            assertThat(option.price_delta).isEqualByComparingTo("3.25");
                        });
                    }
                    assertThat(dataSource.borrowed.get()).isEqualTo(2);
                    assertThat(pool.getHikariPoolMXBean().getActiveConnections()).isZero();
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                } finally {
                    executor.shutdownNow();
                }
            }
        }
    }

    private static void assertReadOnlyTransaction() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
        assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
    }

    private static final class CountingDataSource extends DelegatingDataSource {
        private final AtomicInteger borrowed = new AtomicInteger();

        CountingDataSource(HikariDataSource target) { super(target); }

        @Override
        public Connection getConnection() throws SQLException {
            borrowed.incrementAndGet();
            return super.getConnection();
        }
    }
}
