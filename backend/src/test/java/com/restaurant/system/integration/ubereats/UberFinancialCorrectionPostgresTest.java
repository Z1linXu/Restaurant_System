package com.restaurant.system.integration.ubereats;

import com.fasterxml.jackson.databind.*;
import com.restaurant.system.integration.ubereats.dto.UberFinancialSnapshot;
import com.restaurant.system.integration.ubereats.entity.UberEatsOrder;
import com.restaurant.system.integration.ubereats.config.UberEatsProperties;
import com.restaurant.system.integration.ubereats.repository.*;
import com.restaurant.system.integration.ubereats.service.*;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.repository.StoreRepository;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@EnabledIfEnvironmentVariable(named="PENDING_TEST_POSTGRES_URL",matches="jdbc:postgresql://127.0.0.1:55483/.*")
class UberFinancialCorrectionPostgresTest {
    static final ObjectMapper JSON=new ObjectMapper();
    static String url,dbName,user;
    static JdbcTemplate db;
    static List<JsonNode> fixture;
    @BeforeAll static void start() throws Exception {
        String base=System.getenv("PENDING_TEST_POSTGRES_URL");user=System.getProperty("user.name");
        dbName="pending_"+UUID.randomUUID().toString().replace("-","");
        try(var c=DriverManager.getConnection(base,user,"");var s=c.createStatement()){s.execute("CREATE DATABASE "+dbName);}
        url=base.substring(0,base.lastIndexOf('/')+1)+dbName;
        Flyway.configure().dataSource(url,user,"").locations("classpath:db/migration").load().migrate();
        db=new JdbcTemplate(new DriverManagerDataSource(url,user,""));
        db.update("INSERT INTO organizations(id,name,code,status,created_at,updated_at) VALUES(9001,'Synthetic','SYNTHETIC','active',now(),now())");
        for(int store=1;store<=3;store++)
            db.update("INSERT INTO stores(id,name,code,status,organization_id,created_at,updated_at) VALUES(?,? ,?,'active',9001,now(),now())",store,"Synthetic "+store,"SYNTHETIC_"+store);
        db.update("INSERT INTO uber_eats_store_mappings(id,environment,uber_store_id,store_id,organization_id,enabled,created_at) VALUES(9001,'production','synthetic-store',1,9001,true,now())");
        fixture=new ArrayList<>();
        JsonNode input=System.getProperty("pending.private.fixture")==null
            ? JSON.readTree(UberFinancialCorrectionPostgresTest.class.getResourceAsStream("/uber/pending-20261103-sanitized.json"))
            : JSON.readTree(java.nio.file.Files.readString(java.nio.file.Path.of(System.getProperty("pending.private.fixture"))));
        input.forEach(fixture::add);
        for(var f:fixture) {
            var original=JSON.readValue(f.path("original_snapshot_json").asText(),UberFinancialSnapshot.class);
            var raw=JSON.createObjectNode();var items=raw.putArray("items");
            for(var line:original.items())items.addObject().put("id",line.id()).put("quantity",line.quantity()).put("title","Synthetic");
            db.update("""
                INSERT INTO uber_eats_orders(id,environment,store_mapping_id,store_id,uber_store_id,uber_order_id,status,
                attempt_count,cancelled,edit_required,scheduled,placed_at,accepted_observed_at,next_attempt_at,created_at,updated_at,
                item_financial_snapshot_json,raw_financial_snapshot_json,raw_order_snapshot_json)
                VALUES(?,'production',9001,1,?,?,'RELEASED_TO_KITCHEN',0,false,false,false,
                '2026-11-03 12:00:00','2026-11-03 12:01:00',now(),now(),now(),?,?,?)
                """,f.path("id").asLong(),original.store_id(),original.order_id(),f.path("original_snapshot_json").asText(),
                    f.path("source_charges_json").asText(),JSON.writeValueAsString(raw));
        }
    }
    @AfterAll static void stop() throws Exception {
        if(url==null)return;
        String admin=url.substring(0,url.lastIndexOf('/')+1)+"postgres";
        try(var c=DriverManager.getConnection(admin,user,"");var s=c.createStatement()){s.execute("DROP DATABASE "+dbName);}
    }

    @Test void previewApplyReplayCompensationAndStoreIsolationPreserveAllBusinessRows() throws Exception {
        String before=businessFingerprint();
        try(var c=DriverManager.getConnection(url,user,"")) {
            var preview=UberFinancialCorrectionCli.execute(c,"preview",null);
            assertThat(preview.orders()).hasSize(14);
            // Separate invocations must bind identical bytes, not JVM-randomized Set iteration.
            for (int process=0;process<3;process++) {
                var fork=new ProcessBuilder(System.getProperty("java.home")+"/bin/java",
                    "-Dpending.jdbc.url="+url,"-Dpending.db.user="+user,"-cp",
                    System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")),
                    UberFinancialCorrectionCli.class.getName(),"--mode","preview").redirectError(ProcessBuilder.Redirect.INHERIT).start();
                String output=new String(fork.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
                assertThat(fork.waitFor()).isZero();
                assertThat(JSON.readTree(output).path("previewSha256").asText()).isEqualTo(preview.previewSha256());
            }
            assertThat(preview.orders().stream().filter(x->x.allowed()).count()).isEqualTo(8);
            assertThat(preview.orders().stream().filter(x->!x.allowed()).map(x->x.validation()).distinct())
                    .containsExactly("PROMO_TOTAL_RECONCILIATION_REQUIRED");
            assertThat(db.queryForObject("SELECT count(*) FROM uber_eats_financial_corrections",Integer.class)).isZero();
            assertThatThrownBy(()->UberFinancialCorrectionCli.execute(c,"apply","wrong")).hasMessage("PREVIEW_CHANGED");
            var applied=UberFinancialCorrectionCli.execute(c,"apply",preview.previewSha256());
            assertThat(applied.inserted()).isEqualTo(8);
            assertThat(UberFinancialCorrectionCli.execute(c,"apply",preview.previewSha256()).inserted()).isZero();
            assertThat(businessFingerprint()).isEqualTo(before);
            var sales=readSales(1L);
            assertThat(sales).hasSize(14);
            assertThat(sales.stream().filter(x->x.revenueMinor()!=null).count()).isEqualTo(8);
            long expected=preview.orders().stream().filter(x->x.allowed()).mapToLong(x->x.candidateMinor()).sum();
            assertThat(sales.stream().filter(x->x.revenueMinor()!=null).mapToLong(x->x.revenueMinor()).sum()).isEqualTo(expected);
            assertThat(readSales(2L)).isEmpty();
            assertThat(new UberFinancialCorrectionRepository(db).latest("sandbox",1L,fixture.stream().map(f->f.path("id").asLong()).toList())).isEmpty();
            String v2Fingerprint=db.queryForObject("SELECT md5(string_agg(row_to_json(t)::text,'' ORDER BY id)) FROM uber_eats_financial_corrections t WHERE snapshot_version=2",String.class);
            var totalPreview=UberFinancialCorrectionCli.executeOrderTotal(c,"preview",null);
            if(System.getProperty("pending.preview.output")!=null)
                java.nio.file.Files.writeString(java.nio.file.Path.of(System.getProperty("pending.preview.output")),JSON.writeValueAsString(totalPreview),java.nio.file.StandardOpenOption.CREATE_NEW);
            assertThat(totalPreview.orders()).hasSize(6).allSatisfy(p->{
                assertThat(p.allowed()).isTrue();assertThat(p.componentDeltaMinor()).isIn(-1L,1L);
                assertThat(p.itemAmountStatus()).isEqualTo("PENDING_RECONCILIATION");
            });
            assertThat(totalPreview.orders().stream().mapToLong(x->x.candidateMinor()).sum()).isEqualTo(31541);
            for(int process=0;process<2;process++) {
                var fork=new ProcessBuilder(System.getProperty("java.home")+"/bin/java",
                    "-Dpending.jdbc.url="+url,"-Dpending.db.user="+user,"-cp",
                    System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")),
                    UberFinancialCorrectionCli.class.getName(),"--mode","preview","--case","order-total-v3")
                    .redirectError(ProcessBuilder.Redirect.INHERIT).start();
                String output=new String(fork.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
                assertThat(fork.waitFor()).isZero();
                assertThat(JSON.readTree(output).path("previewSha256").asText()).isEqualTo(totalPreview.previewSha256());
            }
            assertThatThrownBy(()->UberFinancialCorrectionCli.executeOrderTotal(c,"apply",preview.previewSha256())).hasMessage("PREVIEW_CHANGED");
            assertThat(UberFinancialCorrectionCli.executeOrderTotal(c,"apply",totalPreview.previewSha256()).inserted()).isEqualTo(6);
            assertThat(UberFinancialCorrectionCli.executeOrderTotal(c,"apply",totalPreview.previewSha256()).inserted()).isZero();
            var all=readSales(1L);
            assertThat(all).hasSize(14).allSatisfy(x->assertThat(x.revenueMinor()).isNotNull());
            assertThat(all.stream().mapToLong(x->x.revenueMinor()).sum()).isEqualTo(61534);
            assertThat(all.stream().filter(x->x.itemPendingReason()!=null).count()).isEqualTo(6);
            all.stream().filter(x->x.itemPendingReason()!=null).forEach(x->assertThat(x.items()).allSatisfy(i->assertThat(i.revenueMinor()).isNull()));
            assertThat(businessFingerprint()).isEqualTo(before);
            assertThat(db.queryForObject("SELECT md5(string_agg(row_to_json(t)::text,'' ORDER BY id)) FROM uber_eats_financial_corrections t WHERE snapshot_version=2",String.class)).isEqualTo(v2Fingerprint);
            assertThat(readSales(2L)).isEmpty();
            String v3Source=db.queryForObject("SELECT raw_financial_snapshot_json FROM uber_eats_orders WHERE id=693",String.class);
            db.update("UPDATE uber_eats_orders SET raw_financial_snapshot_json=? WHERE id=693",v3Source+" ");
            assertThat(readSales(1L).stream().filter(x->x.revenueMinor()!=null).count()).isEqualTo(13);
            assertThatThrownBy(()->UberFinancialCorrectionCli.executeOrderTotal(c,"apply",totalPreview.previewSha256())).hasMessage("PREVIEW_CHANGED");
            assertThat(UberFinancialCorrectionCli.executeOrderTotal(c,"revoke",totalPreview.previewSha256()).inserted()).isEqualTo(6);
            assertThat(UberFinancialCorrectionCli.executeOrderTotal(c,"revoke",totalPreview.previewSha256()).inserted()).isZero();
            db.update("UPDATE uber_eats_orders SET raw_financial_snapshot_json=? WHERE id=693",v3Source);
            assertThatThrownBy(()->UberFinancialCorrectionCli.executeOrderTotal(c,"apply",totalPreview.previewSha256())).hasMessage("BATCH_ALREADY_REVOKED");
            assertThat(readSales(1L).stream().filter(x->x.revenueMinor()!=null).count()).isEqualTo(8);
            assertThat(businessFingerprint()).isEqualTo(before);
            // Evidence drift must fail closed in reads, without deleting or rewriting any original.
            long changed=fixture.stream().filter(f->preview.orders().stream().anyMatch(p->p.allowed()&&p.auditId().equals(f.path("audit_id").asText()))).findFirst().orElseThrow().path("id").asLong();
            String charges=db.queryForObject("SELECT raw_financial_snapshot_json FROM uber_eats_orders WHERE id=?",String.class,changed);
            db.update("UPDATE uber_eats_orders SET raw_financial_snapshot_json=? WHERE id=?",charges+" ",changed);
            assertThat(readSales(1L).stream().filter(x->x.revenueMinor()!=null).count()).isEqualTo(7);
            assertThat(UberFinancialCorrectionCli.execute(c,"revoke",preview.previewSha256()).inserted()).isEqualTo(8);
            assertThat(UberFinancialCorrectionCli.execute(c,"revoke",preview.previewSha256()).inserted()).isZero();
            assertThat(readSales(1L)).allSatisfy(s->assertThat(s.revenueMinor()).isNull());
            db.update("UPDATE uber_eats_orders SET raw_financial_snapshot_json=? WHERE id=?",charges,changed);
            assertThat(businessFingerprint()).isEqualTo(before);
            assertThat(db.queryForObject("SELECT count(*) FROM uber_eats_financial_corrections",Integer.class)).isEqualTo(28);
            // The original application has no correction migration; Flyway future-migration compatibility is required for rollback.
            var locations=java.nio.file.Files.createTempDirectory("pending-old-flyway-");
            try(var resources=java.nio.file.Files.list(java.nio.file.Path.of("src/main/resources/db/migration"))) {
                for(var path:resources.toList())if(!path.getFileName().toString().startsWith("V39_1__") && !path.getFileName().toString().startsWith("V39_2__"))
                    java.nio.file.Files.copy(path,locations.resolve(path.getFileName()));
            }
            Flyway.configure().dataSource(url,user,"").locations("filesystem:"+locations).load().validate();
            // Current Production v39.1 application also accepts additive v39.2 for app rollback.
            java.nio.file.Files.copy(java.nio.file.Path.of("src/main/resources/db/migration/V39_1__add_uber_financial_corrections.sql"),
                    locations.resolve("V39_1__add_uber_financial_corrections.sql"));
            Flyway.configure().dataSource(url,user,"").locations("filesystem:"+locations).load().validate();
        }
    }

    private String businessFingerprint() {
        return db.queryForObject("SELECT md5(string_agg(row_to_json(t)::text,'' ORDER BY id)) FROM uber_eats_orders t",String.class)
            +db.queryForObject("SELECT count(*) FROM orders",Long.class)
            +db.queryForObject("SELECT count(*) FROM print_jobs",Long.class)
            +db.queryForObject("SELECT count(*) FROM order_dispatch_outbox",Long.class)
            +db.queryForObject("SELECT count(*) FROM kitchen_tasks",Long.class);
    }
    private List<com.restaurant.system.analytics.service.UberSalesSource.Sale> readSales(Long requestedStore) {
        var repository=mock(UberEatsOrderRepository.class);var stores=mock(StoreRepository.class);
        Store store=new Store();store.id=requestedStore;store.timezone="America/Toronto";
        when(stores.findById(requestedStore)).thenReturn(Optional.of(store));
        var rows=db.query("SELECT * FROM uber_eats_orders WHERE store_id=?", (r,n)->{
            var row=new UberEatsOrder();row.id=r.getLong("id");row.environment=r.getString("environment");row.storeId=r.getLong("store_id");
            row.uberOrderId=r.getString("uber_order_id");row.uberStoreId=r.getString("uber_store_id");row.status=r.getString("status");
            row.placedAt=r.getTimestamp("placed_at").toLocalDateTime();row.acceptedObservedAt=row.placedAt;
            row.itemFinancialSnapshotJson=r.getString("item_financial_snapshot_json");row.rawFinancialSnapshotJson=r.getString("raw_financial_snapshot_json");
            row.rawOrderSnapshotJson=r.getString("raw_order_snapshot_json");return row;
        },requestedStore);
        when(repository.salesWindow(anyString(),anyLong(),any(),any())).thenReturn(rows);
        var config=new UberEatsProperties();config.environment="production";
        return new UberFinancialSalesSource(repository,stores,config,JSON,new UberFinancialCorrectionRepository(db))
                .load(List.of(requestedStore),LocalDateTime.parse("2026-11-03T00:00:00"),LocalDateTime.parse("2026-11-04T00:00:00"));
    }
}
