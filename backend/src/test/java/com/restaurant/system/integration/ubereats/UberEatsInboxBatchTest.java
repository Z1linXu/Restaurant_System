package com.restaurant.system.integration.ubereats;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.restaurant.system.integration.ubereats.entity.UberEatsOrder;
import com.restaurant.system.integration.ubereats.repository.UberEatsInboxReadRepository;
import com.restaurant.system.integration.ubereats.repository.UberEatsInboxReadRepository.*;
import com.restaurant.system.integration.ubereats.service.*;
import com.restaurant.system.order.dto.CreateOrderRequest;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class UberEatsInboxBatchTest {
    @Test void twoHundredRowsUseOneJobReadAndOnlyOneMissingModuleRead() {
        var reads=mock(UberEatsInboxReadRepository.class);
        var tx=mock(UberEatsOrderTransactions.class);
        var view=new UberEatsKitchenView(reads,tx);
        var rows=new ArrayList<UberEatsOrder>(); var jobs=new ArrayList<Job>();
        for(long id=1;id<=200;id++) { rows.add(row(id,1L,"RELEASED_TO_KITCHEN")); jobs.add(new Job(id,"GRAB","PRINTED")); }
        when(reads.latestJobs(eq(1L),any())).thenReturn(jobs);
        when(reads.dispatches(eq(1L),any())).thenReturn(List.of(new Dispatch("submit:1:HOT_KITCHEN","PENDING")));
        var states=view.states(rows);
        assertThat(states).hasSize(200);
        assertThat(states.get(rows.get(0))).isEqualTo(new UberEatsKitchenView.State("PRINT_PARTIAL","PRINTED","PENDING"));
        assertThat(states.get(rows.get(1))).isEqualTo(new UberEatsKitchenView.State("PRINTED","PRINTED","NOT_REQUIRED"));
        verify(reads,times(1)).latestJobs(eq(1L),argThat(ids->ids.size()==200));
        verify(reads,times(1)).dispatches(eq(1L),argThat(keys->keys.size()==200 && keys.stream().allMatch(key->key.endsWith(":HOT_KITCHEN"))));
        verifyNoMoreInteractions(reads);
    }

    @Test void cancellationEditMappingWarningsAndUncertainSemanticsArePreserved() {
        var reads=mock(UberEatsInboxReadRepository.class); var view=new UberEatsKitchenView(reads,mock(UberEatsOrderTransactions.class));
        when(reads.latestJobs(eq(1L),any())).thenAnswer(call->{
            Collection<Long> ids=call.getArgument(1);
            return ids.stream().map(id->new Job(id,"GRAB",id==4?"UNCERTAIN":"PRINTED")).toList();
        });
        var rows=List.of(row(1L,1L,"CANCELLED_AFTER_RELEASE"),row(2L,1L,"EDIT_REVIEW_REQUIRED"),
            row(3L,1L,"KITCHEN_SENT_WITH_MAPPING_WARNINGS"),row(4L,1L,"RELEASED_TO_KITCHEN"));
        var states=view.states(rows);
        for(var row:rows) assertThat(states.get(row).status()).isEqualTo(row.status);
        assertThat(states.get(rows.get(3)).grab()).isEqualTo("UNCERTAIN");
    }

    @Test void storesCannotShareJobOrOutboxStateAndMissingLocalOrdersDoNotQuery() {
        var reads=mock(UberEatsInboxReadRepository.class); var view=new UberEatsKitchenView(reads,mock(UberEatsOrderTransactions.class));
        when(reads.latestJobs(eq(1L),any())).thenReturn(List.of(new Job(7L,"GRAB","PRINTED")));
        when(reads.latestJobs(eq(2L),any())).thenReturn(List.of());
        when(reads.dispatches(eq(2L),any())).thenReturn(List.of(new Dispatch("submit:7:GRAB","PENDING")));
        var a=row(7L,1L,"RELEASED_TO_KITCHEN"); var b=row(7L,2L,"RELEASED_TO_KITCHEN");
        var waiting=row(9L,1L,"WAITING_FOR_ACCEPTANCE"); waiting.localOrderId=null;
        var states=view.states(List.of(a,b,waiting));
        assertThat(states.get(a).status()).isEqualTo("PRINTED");
        assertThat(states.get(b).grab()).isEqualTo("PENDING");
        assertThat(states.get(waiting)).isEqualTo(new UberEatsKitchenView.State("WAITING_FOR_ACCEPTANCE","WAITING","WAITING"));
        verify(reads).latestJobs(1L,List.of(7L)); verify(reads).latestJobs(2L,List.of(7L));
    }

    @Test void controllerContentCanDecodeTheFrozenLocalRequestOnlyOnce() {
        var reads=mock(UberEatsInboxReadRepository.class); var tx=mock(UberEatsOrderTransactions.class);
        var request=new CreateOrderRequest(); request.items=List.of(); request.external_order_note_snapshot="synthetic note";
        when(tx.decode("synthetic",CreateOrderRequest.class)).thenReturn(request);
        var row=row(1L,1L,"RELEASED_TO_KITCHEN"); row.localRequestJson="synthetic";
        var content=new UberEatsKitchenView(reads,tx).content(row);
        assertThat(content.mappedItems()).isEmpty(); assertThat(content.rawItems()).isEmpty();
        assertThat(content.orderNote()).isEqualTo("synthetic note");
        verify(tx,times(1)).decode("synthetic",CreateOrderRequest.class);
    }

    @Test void businessDayBoundariesPreserveDstAndLegacyServerLocalCreation() {
        var zone=ZoneId.of("America/Toronto"); var server=ZoneId.of("Asia/Shanghai");
        var spring=UberEatsInboxDay.of(LocalDate.of(2026,3,8),zone,server);
        var fall=UberEatsInboxDay.of(LocalDate.of(2026,11,1),zone,server);
        assertThat(Duration.between(spring.start(),spring.end()).toHours()).isEqualTo(23);
        assertThat(Duration.between(fall.start(),fall.end()).toHours()).isEqualTo(25);
        assertThat(spring.createdStart()).isEqualTo(LocalDateTime.of(2026,3,8,13,0));
    }

    private UberEatsOrder row(Long id,Long store,String status) {
        var row=new UberEatsOrder(); row.id=id; row.localOrderId=id; row.storeId=store;
        row.status=status; row.processingMode="KITCHEN_MIRROR"; return row;
    }
}
