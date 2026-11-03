package com.restaurant.system.order.finish;

import com.restaurant.system.RestaurantSystemApplication;
import com.restaurant.system.common.auth.AuthenticatedUser;
import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.order.service.impl.OrderServiceImpl;
import com.restaurant.system.printing.service.PrintDispatcherService;
import com.restaurant.system.printing.service.impl.OrderDispatchOutboxProcessor;
import com.restaurant.system.common.realtime.RealtimeEventPublisher;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="FINISH_ALL_POSTGRES_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:.*")
@SpringBootTest(classes=RestaurantSystemApplication.class,properties={
    "spring.profiles.active=finish-all-test","spring.flyway.enabled=true","spring.jpa.hibernate.ddl-auto=validate","spring.jpa.show-sql=false",
    "app.seed.runtime-enabled=false","app.seed.safe-metadata-enabled=false","app.seed.default-users-enabled=false",
    "app.seed.demo-data-enabled=false","app.seed.membership-supplement-enabled=false","app.daily-close.enabled=false",
    "UBER_EATS_ENABLED=false","UBER_EATS_WORKER_ENABLED=false"})
class FinishAllTablesPostgresTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",()->System.getenv("FINISH_ALL_POSTGRES_URL"));
        r.add("spring.datasource.username",()->"xuzilin");r.add("spring.datasource.password",()->"");
    }
    @Autowired JdbcTemplate db;
    @Autowired FinishAllTablesService service;
    @SpyBean OrderServiceImpl orders;
    @MockBean PrintDispatcherService printing;
    @MockBean OrderDispatchOutboxProcessor outbox;
    @MockBean RealtimeEventPublisher realtime;

    @ParameterizedTest @ValueSource(strings={"submitted","preparing","ready"})
    void completesOneEligibleTableUsingExistingSemanticsAndKeepsHistoryAndMoney(String state) {
        var f=fixture(); long id=order(f,"1",state,"dine_in");
        db.update("insert into order_items(order_id,quantity,unit_price,line_amount,status) values (?,2,10,20,'submitted')",id);
        var before=db.queryForMap("select subtotal_amount,total_amount,table_no from orders where id=?",id);
        var preview=service.preview(f.store,f.actor);
        assertThat(preview.order_count()).isEqualTo(1);assertThat(preview.table_slot_count()).isEqualTo(1);
        assertThat(status(id)).isEqualTo(state); // GET/Cancel leaves orders and batch ledger unchanged.
        assertThat(db.queryForObject("select count(*) from order_finish_batches where store_id=?",Integer.class,f.store)).isZero();
        clearInvocations(printing,outbox,realtime,orders);
        var result=service.execute(f.store,f.actor,preview.preview_token());
        assertThat(result.completed()).isEqualTo(1);assertThat(result.failed()).isZero();assertThat(result.released_slot_count()).isEqualTo(1);
        assertThat(status(id)).isEqualTo("completed");assertThat(db.queryForMap("select subtotal_amount,total_amount,table_no from orders where id=?",id)).isEqualTo(before);
        assertThat(db.queryForObject("select count(*) from order_items where order_id=?",Integer.class,id)).isEqualTo(1);
        verify(orders).completeOrder(id);verifyNoInteractions(printing,outbox);
        var event=org.mockito.ArgumentCaptor.forClass(com.restaurant.system.common.realtime.RealtimeUpdateMessage.class);
        verify(realtime).publish(event.capture(),any());
        assertThat(event.getValue().event_type).isEqualTo("order.completed");
        assertThat(event.getValue().order_id).isEqualTo(id);assertThat(event.getValue().store_id).isEqualTo(f.store);
        String audit=db.queryForObject("select metadata_json from audit_logs where store_id=? and action='TABLES_FINISHED'",String.class,f.store);
        assertThat(audit).contains("\"completed\":1",String.valueOf(id)).doesNotContain("preview_token","secret");
    }

    @Test void handlesSplitSlotsAndMultipleTablesWithoutChangingConfiguration() {
        var f=fixture(); long a=order(f,"1-A","preparing","dine_in"),b=order(f,"1-B","ready","dine_in"),c=order(f,"2","submitted","dine_in");
        String tables=db.queryForObject("select jsonb_agg(to_jsonb(d) order by id)::text from dining_tables d where store_id=?",String.class,f.store);
        var p=service.preview(f.store,f.actor);assertThat(p.orders()).extracting(x->x.table_display_name()).contains("1里","1外","2");
        var r=service.execute(f.store,f.actor,p.preview_token());assertThat(r.completed()).isEqualTo(3);assertThat(r.released_slot_count()).isEqualTo(3);
        assertThat(List.of(status(a),status(b),status(c))).containsOnly("completed");assertThat(r.remaining_tables()).isEmpty();
        assertThat(db.queryForObject("select jsonb_agg(to_jsonb(d) order by id)::text from dining_tables d where store_id=?",String.class,f.store)).isEqualTo(tables);
    }
    @Test void emptyPreviewDoesNotModifyOrdersOrPrint() {
        var f=fixture();var p=service.preview(f.store,f.actor);assertThat(p.order_count()).isZero();
        assertThat(p.orders()).isEmpty();assertThat(db.queryForObject("select count(*) from order_finish_batches where store_id=?",Integer.class,f.store)).isZero();
    }
    @Test void excludesCompletedTakeoutDeliveryUberAndOtherStoreButPreservesDraftWarnings() {
        var f=fixture();var other=fixture();long active=order(f,"1","ready","dine_in");
        long done=order(f,"2","completed","dine_in"),draft=order(f,"2","draft","dine_in"),takeout=order(f,"T","ready","pickup"),delivery=order(f,"D","ready","delivery"),uber=order(f,"U","ready","dine_in"),foreign=order(other,"1","ready","dine_in");
        db.update("update orders set external_source='UBER_EATS',financial_mode='EXTERNAL_PLATFORM' where id=?",uber);
        var p=service.preview(f.store,f.actor);assertThat(p.orders()).extracting(x->x.order_id()).containsExactly(active);assertThat(p.blocked_tables()).contains("2");
        var r=service.execute(f.store,f.actor,p.preview_token());assertThat(r.remaining_tables()).contains("2");
        assertThat(status(done)).isEqualTo("completed");assertThat(status(draft)).isEqualTo("draft");
        for(long id:new long[]{takeout,delivery,uber,foreign})assertThat(status(id)).isEqualTo("ready");
    }
    @Test void previewDoesNotIncludeNewlyCreatedOrNewlySubmittedOrders() {
        var f=fixture();long first=order(f,"1","ready","dine_in"),draft=order(f,"2","draft","dine_in");
        var p=service.preview(f.store,f.actor);long fresh=order(f,"1-B","ready","dine_in");db.update("update orders set status='preparing' where id=?",draft);
        var r=service.execute(f.store,f.actor,p.preview_token());assertThat(r.completed()).isEqualTo(1);assertThat(status(first)).isEqualTo("completed");
        assertThat(status(fresh)).isEqualTo("ready");assertThat(status(draft)).isEqualTo("preparing");assertThat(r.released_slot_count()).isZero();
    }
    @Test void revalidatesRevisionTypeAndStatusBeforeCompleting() {
        var f=fixture();long changed=order(f,"1-A","ready","dine_in"),cancelled=order(f,"1-B","ready","dine_in"),moved=order(f,"2","ready","dine_in");
        var p=service.preview(f.store,f.actor);db.update("update orders set current_revision=2 where id=?",changed);
        db.update("update orders set status='cancelled' where id=?",cancelled);db.update("update orders set order_type='pickup' where id=?",moved);
        var r=service.execute(f.store,f.actor,p.preview_token());assertThat(r.completed()).isZero();assertThat(r.skipped()).isEqualTo(3);assertThat(status(changed)).isEqualTo("ready");
    }
    @Test void twoPadsWithTheSameBatchGetIdenticalDurableResultsAndAuditOnce() throws Exception {
        var f=fixture();long id=order(f,"1","ready","dine_in");var p=service.preview(f.store,f.actor);
        var pool=Executors.newFixedThreadPool(2);
        try {
            var a=pool.submit(()->service.execute(f.store,f.actor,p.preview_token()));var b=pool.submit(()->service.execute(f.store,f.actor,p.preview_token()));
            assertThat(a.get(15,TimeUnit.SECONDS).completed()).isEqualTo(1);assertThat(b.get(15,TimeUnit.SECONDS).completed()).isEqualTo(1);
            verify(orders,times(1)).completeOrder(id);
            assertThat(db.queryForObject("select count(*) from audit_logs where store_id=? and action='TABLES_FINISHED'",Integer.class,f.store)).isEqualTo(1);
        } finally {pool.shutdownNow();}
    }
    @Test void twoDistinctPreviewsCannotFinishTheSameOrderTwice() throws Exception {
        var f=fixture();long id=order(f,"1","ready","dine_in");var p=service.preview(f.store,f.actor);var q=service.preview(f.store,f.actor);
        var pool=Executors.newFixedThreadPool(2);
        try {
            var a=pool.submit(()->service.execute(f.store,f.actor,p.preview_token()));var b=pool.submit(()->service.execute(f.store,f.actor,q.preview_token()));
            var x=a.get(15,TimeUnit.SECONDS);var y=b.get(15,TimeUnit.SECONDS);
            assertThat(x.completed()+y.completed()).isEqualTo(1);assertThat(x.skipped()+y.skipped()).isEqualTo(1);verify(orders,times(1)).completeOrder(id);
        } finally {pool.shutdownNow();}
    }
    @Test void partialFailureCommitsSuccessfulOrdersAndRetryDoesNotLieOrRepeat() {
        var f=fixture();long a=order(f,"1-A","ready","dine_in"),b=order(f,"1-B","ready","dine_in");var p=service.preview(f.store,f.actor);
        doThrow(new BusinessException("fixture failure")).when(orders).completeOrder(b);
        var r=service.execute(f.store,f.actor,p.preview_token());assertThat(r.completed()).isEqualTo(1);assertThat(r.failed()).isEqualTo(1);
        assertThat(status(a)).isEqualTo("completed");assertThat(status(b)).isEqualTo("ready");assertThat(r.remaining_tables()).contains("1外");
        var repeated=service.execute(f.store,f.actor,p.preview_token());assertThat(repeated.results()).isEqualTo(r.results());verify(orders,times(1)).completeOrder(a);verify(orders,times(1)).completeOrder(b);
    }
    @Test void timeoutAfterCommitCanRetryTheOriginallyConfirmedSet() {
        var f=fixture();long a=order(f,"1-A","ready","dine_in"),b=order(f,"1-B","ready","dine_in");var p=service.preview(f.store,f.actor);
        var initial=service.execute(f.store,f.actor,p.preview_token());
        var repeated=service.execute(f.store,f.actor,p.preview_token());assertThat(repeated.results()).isEqualTo(initial.results());assertThat(repeated.completed()).isEqualTo(2);
        verify(orders,times(1)).completeOrder(a);verify(orders,times(1)).completeOrder(b);
    }
    @Test void tamperedOrCrossStoreActorPreviewCannotExecute() {
        var f=fixture();var other=fixture();long id=order(f,"1","ready","dine_in");var p=service.preview(f.store,f.actor);
        assertThatThrownBy(()->service.execute(other.store,f.actor,p.preview_token())).hasMessageContaining("SCOPE_MISMATCH");
        assertThatThrownBy(()->service.execute(f.store,other.actor,p.preview_token())).hasMessageContaining("SCOPE_MISMATCH");
        assertThatThrownBy(()->service.execute(f.store,f.actor,p.preview_token()+"x")).hasMessageContaining("PREVIEW_INVALID");assertThat(status(id)).isEqualTo("ready");
    }
    @Test void aServerDraftOnTheWholeTablePreventsClaimingSplitSlotsReleased() {
        var f=fixture();order(f,"1-A","ready","dine_in");order(f,"1","draft","dine_in");
        var p=service.preview(f.store,f.actor);assertThat(p.table_slot_count()).isZero();
        var r=service.execute(f.store,f.actor,p.preview_token());assertThat(r.completed()).isEqualTo(1);assertThat(r.released_slot_count()).isZero();assertThat(r.remaining_tables()).contains("1");
    }
    @Test void interruptedProcessResumesCommittedPrefixWithoutTouchingNewOrders() {
        var f=fixture();long a=order(f,"1-A","ready","dine_in"),b=order(f,"1-B","ready","dine_in");var p=service.preview(f.store,f.actor);
        doThrow(new AssertionError("Simulated process termination")).when(orders).completeOrder(b);
        assertThatThrownBy(()->service.execute(f.store,f.actor,p.preview_token())).isInstanceOf(AssertionError.class);
        assertThat(status(a)).isEqualTo("completed");assertThat(status(b)).isEqualTo("ready");
        long fresh=order(f,"2","ready","dine_in");doCallRealMethod().when(orders).completeOrder(b);
        var r=service.execute(f.store,f.actor,p.preview_token());assertThat(r.completed()).isEqualTo(2);assertThat(status(fresh)).isEqualTo("ready");
        verify(orders,times(1)).completeOrder(a);verifyNoInteractions(printing,outbox);
    }
    @Test void expiredUnconfirmedPreviewIsRejectedButConfirmedExpiredBatchCanResume() {
        var f=fixture();long id=order(f,"1","ready","dine_in");var p=service.preview(f.store,f.actor);
        var expired=new FinishAllTablesResponse.Plan(p.batch_id(),f.store,f.actor.userId(),1L,p.orders());
        Object target=org.springframework.test.util.AopTestUtils.getUltimateTargetObject(service);
        String signed=org.springframework.test.util.ReflectionTestUtils.invokeMethod(target,"sign",expired);
        assertThatThrownBy(()->service.execute(f.store,f.actor,signed)).hasMessageContaining("PREVIEW_EXPIRED");assertThat(status(id)).isEqualTo("ready");
        var json=new com.fasterxml.jackson.databind.ObjectMapper();
        try {db.update("insert into order_finish_batches(id,store_id,actor_user_id,plan_json) values (?,?,?,?)",p.batch_id(),f.store,f.actor.userId(),json.writeValueAsString(expired));}
        catch(Exception e){throw new RuntimeException(e);}
        assertThat(service.execute(f.store,f.actor,signed).completed()).isEqualTo(1);
    }
    private String status(long id) {return db.queryForObject("select status from orders where id=?",String.class,id);}
    private long order(Fixture f,String table,String status,String type) {
        return db.queryForObject("insert into orders(store_id,order_no,order_type,status,table_no,financial_mode,subtotal_amount,total_amount,current_revision,submitted_at,updated_at) values (?,?,?,?,?,'IN_STORE',20,22.99,0,current_timestamp,current_timestamp) returning id",Long.class,f.store,UUID.randomUUID().toString(),type,status,table);
    }
    private Fixture fixture() {
        String code="FA-"+UUID.randomUUID();
        Long org=db.queryForObject("insert into organizations(name,code,status) values ('Finish fixture',?,'active') returning id",Long.class,code);
        Long store=db.queryForObject("insert into stores(organization_id,name,code,status,printing_enabled,printing_mode) values (?,'Finish fixture',?,'active',false,'DISABLED') returning id",Long.class,org,code);
        Long user=db.queryForObject("insert into users(store_id,username,status) values (?,?,'active') returning id",Long.class,store,code);
        db.update("insert into dining_tables(store_id,table_code,table_name,table_config,supports_split,is_active,split_label_mode) values (?,'T1','1','split_supported',true,true,'INSIDE_OUTSIDE'),(?,'T2','2','single_only',false,true,'LEFT_RIGHT')",store,store);
        return new Fixture(store,new AuthenticatedUser(user,store,1L,code,"Fixture user","FRONTDESK"));
    }
    private record Fixture(Long store,AuthenticatedUser actor) {}
}
