package com.restaurant.system.inventory;

import static org.assertj.core.api.Assertions.*;
import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.inventory.entity.InventoryItem;
import com.restaurant.system.inventory.repository.InventoryItemRepository;
import com.restaurant.system.inventory.repository.InventoryTransactionRepository;
import com.restaurant.system.inventory.service.InventoryConsumptionService;
import com.restaurant.system.order.entity.Order;
import com.restaurant.system.support.CommercialPostgres;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

@EnabledIfEnvironmentVariable(named = "COMMERCIAL_TEST_POSTGRES_URL", matches = "jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/commercial_[a-zA-Z0-9_]+")
class InventoryConsumptionPostgresTest {
    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackages = "com.restaurant.system.inventory.repository")
    static class Config {
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds) {
            var bean = new LocalContainerEntityManagerFactoryBean();
            bean.setDataSource(ds); bean.setPackagesToScan("com.restaurant.system.inventory.entity");
            bean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            bean.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop")); return bean;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory emf) { return new JpaTransactionManager(emf); }
        @Bean InventoryConsumptionService consumption(InventoryItemRepository items, InventoryTransactionRepository ledger) {
            return new InventoryConsumptionService(items, ledger);
        }
    }
    CommercialPostgres db;
    AnnotationConfigApplicationContext context;
    InventoryConsumptionService consumption;
    InventoryItemRepository items;
    TransactionTemplate tx;
    JdbcTemplate jdbc;

    @BeforeEach void start() throws Exception {
        db = new CommercialPostgres();
        context = new AnnotationConfigApplicationContext();
        context.registerBean(DataSource.class, () -> db.dataSource); context.register(Config.class); context.refresh();
        consumption = context.getBean(InventoryConsumptionService.class); items = context.getBean(InventoryItemRepository.class);
        tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class)); jdbc = new JdbcTemplate(db.dataSource);
    }
    @AfterEach void stop() throws Exception { if (context != null) context.close(); if (db != null) db.close(); }

    long stock(long store, String amount) {
        return tx.execute(status -> {
            var item = new InventoryItem(); item.store_id = store; item.name = "Synthetic stock";
            item.is_active = true; item.current_stock = new BigDecimal(amount); return items.saveAndFlush(item).id;
        });
    }
    Order order(long id, long store) { var o = new Order(); o.id = id; o.store_id = store; return o; }
    void consume(long id, long store, Map<Long, BigDecimal> demand) {
        tx.executeWithoutResult(status -> consumption.consume(order(id, store), demand, LocalDateTime.now()));
    }

    @Test void concurrentOrdersCannotOverdrawAndLedgerConservesStock() throws Exception {
        long item = stock(101, "5"); var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> task = () -> { start.await(); try { consume(Thread.currentThread().getId(), 101, Map.of(item, new BigDecimal("3"))); return true; }
                catch (BusinessException insufficient) { return false; } };
            var a = executor.submit(task); var b = executor.submit(task); start.countDown();
            assertThat((a.get(10, TimeUnit.SECONDS) ? 1 : 0) + (b.get(10, TimeUnit.SECONDS) ? 1 : 0)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select current_stock from inventory_items where id=?", BigDecimal.class, item)).isEqualByComparingTo("2");
            assertThat(jdbc.queryForObject("select count(*) from inventory_transactions", Integer.class)).isOne();
            assertThat(jdbc.queryForObject("select sum(qty_change) from inventory_transactions", BigDecimal.class)).isEqualByComparingTo("-3");
        } finally { executor.shutdownNow(); }
    }

    @Test void foreignBomAndLaterFailureLeaveNoPartialStockOrLedger() {
        long own = stock(101, "10"), foreign = stock(202, "10");
        assertThatThrownBy(() -> consume(1, 101, Map.of(own, BigDecimal.ONE, foreign, BigDecimal.ONE))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            consumption.consume(order(2, 101), Map.of(own, new BigDecimal("2")), LocalDateTime.now());
            throw new BusinessException("Simulated later Order failure");
        })).isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("select sum(current_stock) from inventory_items", BigDecimal.class)).isEqualByComparingTo("20");
        assertThat(jdbc.queryForObject("select count(*) from inventory_transactions", Integer.class)).isZero();
    }

    @Test void sharedIngredientsAndTransactionRequirementAreEnforced() {
        long item = stock(101, "10");
        consume(1, 101, Map.of(item, new BigDecimal("4")));
        assertThatThrownBy(() -> consumption.consume(order(2, 101), Map.of(item, BigDecimal.ONE), LocalDateTime.now()))
            .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        assertThat(jdbc.queryForObject("select current_stock from inventory_items where id=?", BigDecimal.class, item)).isEqualByComparingTo("6");
    }
}
