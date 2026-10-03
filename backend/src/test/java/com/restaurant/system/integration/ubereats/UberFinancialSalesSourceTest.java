package com.restaurant.system.integration.ubereats;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.integration.ubereats.config.UberEatsProperties;
import com.restaurant.system.integration.ubereats.dto.UberFinancialSnapshot;
import com.restaurant.system.integration.ubereats.entity.UberEatsOrder;
import com.restaurant.system.integration.ubereats.repository.UberEatsOrderRepository;
import com.restaurant.system.integration.ubereats.service.UberFinancialSalesSource;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.repository.StoreRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class UberFinancialSalesSourceTest {
    private final ObjectMapper json = new ObjectMapper();
    private final UberEatsOrderRepository orders = mock(UberEatsOrderRepository.class);
    private final StoreRepository stores = mock(StoreRepository.class);
    private final UberEatsProperties config = new UberEatsProperties();
    private final LocalDateTime start = LocalDateTime.parse("2026-10-03T00:00:00");
    private UberFinancialSalesSource source() {
        config.environment = "sandbox";
        Store store = new Store(); store.id = 1L; store.timezone = "America/Toronto";
        when(stores.findById(1L)).thenReturn(Optional.of(store));
        return new UberFinancialSalesSource(orders, stores, config, json);
    }
    private UberEatsOrder row() throws Exception {
        UberEatsOrder row = new UberEatsOrder();
        row.storeId=1L; row.environment="sandbox"; row.uberStoreId="store"; row.uberOrderId="order";
        row.status="KITCHEN_SENT"; row.placedAt=LocalDateTime.parse("2026-10-03T22:15:00");
        row.acceptedObservedAt=LocalDateTime.parse("2026-10-03T22:20:00");
        row.rawOrderSnapshotJson="{\"items\":[{\"id\":\"noodle\",\"title\":\"Noodle\",\"quantity\":2}]}";
        row.localRequestJson="{\"store_id\":1,\"items\":[{\"quantity\":2,\"item_sku_snapshot\":\"traditional_beef_noodle\",\"category_code_snapshot\":\"SOUP_NOODLE\",\"item_name_snapshot_en\":\"Noodle\",\"unit_price_snapshot\":99999}]}";
        row.itemFinancialSnapshotJson=json.writeValueAsString(new UberFinancialSnapshot(1,"order","store","2026-10-03T22:15:00Z","CAD",2300L,null,json.createObjectNode(),List.of(new UberFinancialSnapshot.Line("noodle","one",2,json.createObjectNode(),List.of(),2300L))));
        return row;
    }
    @Test void usesPlacedTimeAndFrozenIdentityButNeverLocalPriceAndDeduplicatesOrder() throws Exception {
        var source=source(); var row=row();
        when(orders.salesWindow(anyString(),anyLong(),any(),any())).thenReturn(List.of(row,row));
        var sales=source.load(List.of(1L,1L),start,start.plusDays(1));
        assertThat(sales).hasSize(1);
        assertThat(sales.get(0).placedAt()).isEqualTo(LocalDateTime.parse("2026-10-03T18:15:00"));
        assertThat(sales.get(0).revenueMinor()).isEqualTo(2300L);
        assertThat(sales.get(0).items().get(0).quantity()).isEqualTo(2);
        assertThat(sales.get(0).items().get(0).sku()).isEqualTo("traditional_beef_noodle");
        verify(orders).salesWindow("sandbox",1L,start.plusHours(4),start.plusDays(1).plusHours(4));
    }
    @Test void missingOrMismatchedFinancialEvidenceKeepsQuantityWithUnknownRevenue() throws Exception {
        var source=source(); var row=row(); row.itemFinancialSnapshotJson=null;
        row.financialTotalMinor=9999L; row.financialCurrency="CAD";
        when(orders.salesWindow(anyString(),anyLong(),any(),any())).thenReturn(List.of(row));
        var sale=source.load(List.of(1L),start,start.plusDays(1)).get(0);
        assertThat(sale.revenueMinor()).isNull(); assertThat(sale.items().get(0).revenueMinor()).isNull();
        assertThat(sale.items().get(0).quantity()).isEqualTo(2);
    }
    @Test void rawFallbackRemainsOtherAndCannotUseLaterMenuMappings() throws Exception {
        var source=source(); var row=row();
        row.localRequestJson="{\"store_id\":1,\"items\":[{\"quantity\":2,\"external_kitchen_snapshot\":{\"rawRoot\":true},\"item_sku_snapshot\":null}]}";
        when(orders.salesWindow(anyString(),anyLong(),any(),any())).thenReturn(List.of(row));
        var item=source.load(List.of(1L),start,start.plusDays(1)).get(0).items().get(0);
        assertThat(item.categoryCode()).isEqualTo("OTHER"); assertThat(item.sku()).isNull();
        assertThat(item.revenueMinor()).isEqualTo(2300L);
    }
    @Test void excludesOtherEnvironmentStoreCancelledEditedAndNotAccepted() throws Exception {
        var source=source(); var foreign=row(); foreign.storeId=2L;
        var production=row(); production.environment="production";
        var cancelled=row(); cancelled.cancelled=true;
        var edited=row(); edited.editRequired=true;
        var waiting=row(); waiting.acceptedObservedAt=null;
        when(orders.salesWindow(anyString(),anyLong(),any(),any())).thenReturn(List.of(foreign,production,cancelled,edited,waiting));
        assertThat(source.load(List.of(1L),start,start.plusDays(1))).isEmpty();
    }
    @Test void releaseEventAloneIsNotEvidenceOfAcceptedSale() throws Exception {
        var source=source(); var created=row(); created.acceptedObservedAt=null;
        created.releasedAt=created.placedAt.plusMinutes(1); created.currentState="CREATED";
        created.status="WAITING_FOR_ACCEPTANCE";
        var unknown=row(); unknown.acceptedObservedAt=null; unknown.releasedAt=unknown.placedAt.plusMinutes(1);
        unknown.currentState="UNKNOWN"; unknown.status="EXTERNAL_STATE_REVIEW_REQUIRED";
        when(orders.salesWindow(anyString(),anyLong(),any(),any())).thenReturn(List.of(created,unknown));
        assertThat(source.load(List.of(1L),start,start.plusDays(1))).isEmpty();
    }
}
