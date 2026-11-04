package com.restaurant.system.integration.ubereats;

import static org.assertj.core.api.Assertions.*;
import com.restaurant.system.integration.ubereats.repository.UberEatsInboxReadRepository;
import com.restaurant.system.integration.ubereats.service.UberEatsInboxDay;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Synthetic scratch database only; no Spring startup, Worker, printer, or Uber client. */
@EnabledIfEnvironmentVariable(named="RESOURCE_TEST_POSTGRES_URL", matches="jdbc:postgresql://(?:127\\.0\\.0\\.1|localhost):.*")
class UberEatsInboxReadPostgresTest {
    static String admin,url,name,user; static JdbcTemplate db; static UberEatsInboxReadRepository reads;
    static final UberEatsInboxDay DAY=UberEatsInboxDay.of(LocalDate.of(2026,11,3),ZoneId.of("America/Toronto"),ZoneId.of("Asia/Shanghai"));
    @BeforeAll static void start() throws Exception {
        admin=System.getenv("RESOURCE_TEST_POSTGRES_URL"); user=System.getenv("USER");
        name="inbox_synthetic_"+UUID.randomUUID().toString().replace("-","");
        try(var c=DriverManager.getConnection(admin,user,"");var s=c.createStatement()){s.execute("CREATE DATABASE "+name);}
        url=admin.substring(0,admin.lastIndexOf('/')+1)+name;
        var source=new DriverManagerDataSource(url,user,""); db=new JdbcTemplate(source);
        reads=new UberEatsInboxReadRepository(new NamedParameterJdbcTemplate(source));
        db.execute("CREATE TABLE orders(id bigint primary key,store_id bigint,status text)");
        db.execute("CREATE TABLE print_jobs(id bigint primary key,store_id bigint,order_id bigint,module_code text,status text,created_at timestamp,payload_snapshot text,rendered_text_snapshot text,escpos_payload_base64 text)");
        db.execute("CREATE TABLE order_dispatch_outbox(store_id bigint,source_key text unique,status text)");
        db.execute("""
            CREATE TABLE uber_eats_orders(id bigint primary key,environment text,store_id bigint,status text,
            display_id text,uber_order_id text,mapping_status text,mapping_error text,last_error text,local_order_id bigint,
            placed_at timestamp,created_at timestamp,accepted_at timestamp,cancelled_at timestamp,raw_order_snapshot_json text,
            processing_mode text,customer_display_name text,released_at timestamp,accepted_observed_at timestamp,
            local_request_json text,kitchen_dispatched_at timestamp,cancelled boolean default false,edit_required boolean default false)
            """);
    }
    @AfterAll static void stop() throws Exception {
        if(name==null)return;
        try(var c=DriverManager.getConnection(admin,user,"");var s=c.createStatement()){s.execute("DROP DATABASE "+name);}
    }
    @BeforeEach void clear() { for(String table:List.of("print_jobs","order_dispatch_outbox","uber_eats_orders","orders"))db.update("DELETE FROM "+table); }

    @Test void narrowProjectionKeepsCreatedAtThenIdOrderingAndStoreIsolation() {
        job(10,1,100,"GRAB","FAILED","2026-11-03 12:00:00");
        job(11,1,100,"GRAB","PRINTED","2026-11-03 12:00:00");
        job(12,2,100,"GRAB","PENDING","2026-11-03 13:00:00");
        job(9,1,100,"HOT_KITCHEN","PRINTED","2026-11-03 15:00:00");
        job(20,1,100,"HOT_KITCHEN","FAILED","2026-11-03 14:00:00");
        assertThat(reads.latestJobs(1L,List.of(100L))).containsExactlyInAnyOrder(
            new UberEatsInboxReadRepository.Job(100L,"GRAB","PRINTED"),
            new UberEatsInboxReadRepository.Job(100L,"HOT_KITCHEN","PRINTED"));
        db.update("INSERT INTO order_dispatch_outbox VALUES(2,'submit:100:GRAB','FAILED')");
        assertThat(reads.dispatches(1L,List.of("submit:100:GRAB"))).isEmpty();
    }

    @Test void todayAndExplicitHistoryUseUtcAndLegacyCreationBoundsForEveryMode() {
        uber(1,1,"ACCEPTED",null,"2026-11-03 05:00:00","ORDER_MANAGER");
        uber(2,1,"ACCEPTED",null,"2026-11-04 04:59:59","ORDER_MANAGER");
        uber(3,1,"ACCEPTED",null,"2026-11-04 05:00:00","ORDER_MANAGER");
        uber(4,2,"RELEASED_TO_KITCHEN",null,"2026-11-03 15:00:00","KITCHEN_MIRROR");
        uber(5,1,"DENIED",null,null,"KITCHEN_MIRROR");
        db.update("UPDATE uber_eats_orders SET created_at='2026-11-03 13:00:00' WHERE id=5");
        assertThat(reads.page("sandbox",1L,DAY,"today",null,100).rows()).extracting(o->o.id).containsExactly(5L,2L,1L);
        var tomorrow=UberEatsInboxDay.of(LocalDate.of(2026,11,4),ZoneId.of("America/Toronto"),ZoneId.of("Asia/Shanghai"));
        assertThat(reads.page("sandbox",1L,tomorrow,"history",null,100).rows()).extracting(o->o.id).containsExactly(3L);
    }

    @Test void oldUnfinishedAndCancelEditPrintExceptionsRemainInAttentionButPrintedHistoryDoesNot() {
        uber(1,1,"WAITING_FOR_ACCEPTANCE",null,"2026-11-01 15:00:00","KITCHEN_MIRROR");
        uber(2,1,"CANCELLED_AFTER_RELEASE",102L,"2026-11-01 15:00:00","KITCHEN_MIRROR");
        uber(3,1,"EDIT_REVIEW_REQUIRED",103L,"2026-11-03 15:00:00","KITCHEN_MIRROR");
        uber(4,1,"RELEASED_TO_KITCHEN",104L,"2026-11-01 15:00:00","KITCHEN_MIRROR");
        uber(5,1,"RELEASED_TO_KITCHEN",105L,"2026-11-01 15:00:00","KITCHEN_MIRROR");
        uber(6,1,"RELEASED_TO_KITCHEN",106L,"2026-11-01 15:00:00","KITCHEN_MIRROR");
        uber(7,2,"WAITING_FOR_ACCEPTANCE",null,"2026-11-01 15:00:00","KITCHEN_MIRROR");
        for(int id=102;id<=106;id++) { db.update("INSERT INTO orders VALUES(?,1,?)",id,id==105?"preparing":"completed"); job(id,1,id,"GRAB",id==106?"FAILED":"PRINTED","2026-11-01 16:00:00"); }
        assertThat(reads.page("sandbox",1L,DAY,"attention",null,100).rows()).extracting(o->o.id).containsExactly(6L,5L,3L,2L,1L);
    }

    @Test void allCurrentReviewStatesUnknownStatesAndBrokenLocalReferencesRemainVisible() {
        List<String> statuses=List.of("DECISION_REVIEW_REQUIRED","CANCELLED_REVIEW_REQUIRED","SCHEDULED_REVIEW_REQUIRED",
            "CUTOVER_REVIEW_REQUIRED","FUTURE_UNKNOWN_STATE");
        for(int i=0;i<statuses.size();i++)uber(i+1,1,statuses.get(i),null,"2026-11-03 15:00:00","KITCHEN_MIRROR");
        uber(6,1,"ACCEPTED",999L,"2026-11-01 15:00:00","ORDER_MANAGER");
        uber(7,1,"RELEASED_TO_KITCHEN",null,"2026-11-03 15:00:00","KITCHEN_MIRROR");
        uber(8,1,"WAITING_FOR_RELEASE",null,null,"KITCHEN_MIRROR");
        db.update("UPDATE uber_eats_orders SET created_at=null WHERE id=8");
        assertThat(reads.page("sandbox",1L,DAY,"attention",null,100).rows()).extracting(o->o.id)
            .containsExactly(8L,7L,6L,5L,4L,3L,2L,1L);
    }

    @Test void missingHotJobRetainsItsOutboxFallbackInAttentionEvenAfterLocalOrderCompletion() {
        for(int id=1;id<=2;id++) {
            uber(id,1,"RELEASED_TO_KITCHEN",100L+id,id==1?"2026-11-01 15:00:00":"2026-11-03 15:00:00","KITCHEN_MIRROR");
            db.update("INSERT INTO orders VALUES(?,1,'completed')",100L+id);
            job(id,1,100L+id,"GRAB","PRINTED","2026-11-01 16:00:00");
            db.update("INSERT INTO order_dispatch_outbox VALUES(1,?,?)","submit:"+(100L+id)+":HOT_KITCHEN",id==1?"PENDING":"POLICY_BLOCKED");
        }
        assertThat(reads.page("sandbox",1L,DAY,"attention",null,100).rows()).extracting(o->o.id).containsExactly(2L,1L);
        // A later durable job takes precedence over an obsolete Outbox failure, exactly as in view().
        job(3,1,102,"HOT_KITCHEN","PRINTED","2026-11-03 16:00:00");
        assertThat(reads.page("sandbox",1L,DAY,"attention",null,100).rows()).extracting(o->o.id).containsExactly(1L);
    }

    @Test void attentionPaginationExposesEveryUnresolvedRowWithoutDuplicates() {
        for(int id=1;id<=230;id++)uber(id,1,"WAITING_FOR_RELEASE",null,"2026-11-01 15:00:00","KITCHEN_MIRROR");
        var first=reads.page("sandbox",1L,DAY,"attention",null,100);
        var second=reads.page("sandbox",1L,DAY,"attention",first.nextBeforeId(),100);
        var third=reads.page("sandbox",1L,DAY,"attention",second.nextBeforeId(),100);
        assertThat(first.hasMore()).isTrue(); assertThat(second.hasMore()).isTrue(); assertThat(third.hasMore()).isFalse();
        var ids=new ArrayList<Long>(); for(var page:List.of(first,second,third))page.rows().forEach(o->ids.add(o.id));
        assertThat(ids).hasSize(230).doesNotHaveDuplicates(); assertThat(ids.get(229)).isEqualTo(1L);
        assertThat(third.nextBeforeId()).isNull();
    }

    private void uber(long id,long store,String status,Long local,String placed,String mode) {
        db.update("INSERT INTO uber_eats_orders(id,environment,store_id,status,local_order_id,placed_at,created_at,processing_mode) VALUES(?,'sandbox',?,?,?,?,timestamp '2026-11-03 00:00:00',?)",
            id,store,status,local,placed==null?null:Timestamp.valueOf(placed),mode);
    }
    private void job(long id,long store,long order,String module,String status,String created) {
        db.update("INSERT INTO print_jobs VALUES(?,?,?,?,?,?,repeat('synthetic',1000),repeat('synthetic',1000),repeat('synthetic',1000))",id,store,order,module,status,Timestamp.valueOf(created));
    }
}
