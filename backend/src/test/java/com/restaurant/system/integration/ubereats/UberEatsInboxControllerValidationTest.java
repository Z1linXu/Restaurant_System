package com.restaurant.system.integration.ubereats;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.restaurant.system.audit.service.AuditLogService;
import com.restaurant.system.common.auth.*;
import com.restaurant.system.common.exception.GlobalExceptionHandler;
import com.restaurant.system.integration.ubereats.config.UberEatsProperties;
import com.restaurant.system.integration.ubereats.controller.UberEatsInboxController;
import com.restaurant.system.integration.ubereats.mapping.UberEatsMenuMappingService;
import com.restaurant.system.integration.ubereats.repository.*;
import com.restaurant.system.integration.ubereats.service.*;
import com.restaurant.system.modules.StoreModuleAccessEvaluator;
import com.restaurant.system.user.repository.StoreRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Exercises the real catch-all advice, so malformed new query parameters cannot become500. */
class UberEatsInboxControllerValidationTest {
    private static final String URL="/api/v1/stores/1/integrations/uber-eats/inbox";
    private final AuthorizationService auth=mock(AuthorizationService.class);
    private final StoreAccessService access=mock(StoreAccessService.class);
    private final StoreModuleAccessEvaluator modules=mock(StoreModuleAccessEvaluator.class);
    private final UberEatsInboxReadRepository reads=mock(UberEatsInboxReadRepository.class);
    private final StoreRepository stores=mock(StoreRepository.class);
    private final UberEatsOrderRepository orders=mock(UberEatsOrderRepository.class);
    private final UberEatsKitchenView kitchen=mock(UberEatsKitchenView.class);
    private final AuthenticatedUser actor=new AuthenticatedUser(10L,1L,1L,"frontdesk","Frontdesk","FRONTDESK");
    private MockMvc mvc;

    @BeforeEach void setup() {
        when(auth.requireFrontdeskAccessForStore(1L,Capability.ORDER_VIEW_ACTIVE)).thenReturn(actor);
        var controller=new UberEatsInboxController(auth,access,new UberEatsProperties(),orders,
            mock(UberEatsOrderTransactions.class),mock(UberEatsOrderImportService.class),
            mock(UberEatsConfigurationService.class),mock(UberEatsMenuMappingService.class),modules,
            mock(AuditLogService.class),kitchen,reads,stores);
        mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test void malformedOrOutOfRangeLimitsReturn400WithoutAnyDataRead() throws Exception {
        for(String value:List.of("0","201","-1","abc","1.5","2147483648","9223372036854775808",""," 1","+1")) {
            mvc.perform(get(URL).param("limit",value)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("INVALID_INBOX_PAGE"));
        }
        verifyNoInteractions(reads,stores,orders,kitchen);
    }

    @Test void allCursorParametersRejectMalformedZeroNegativeAndOverflowWithoutReads() throws Exception {
        for(String parameter:List.of("today_before_id","attention_before_id","history_before_id")) {
            for(String value:List.of("0","-1","abc","1.5","9223372036854775808",""," 1","+1")) {
                mvc.perform(get(URL).param(parameter,value).param("history_date","2026-11-03"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("INVALID_INBOX_PAGE"));
            }
        }
        verifyNoInteractions(reads,stores,orders,kitchen);
    }

    @Test void malformedAndImpossibleDatesReturn400WithoutAnyDataRead() throws Exception {
        for(String value:List.of("2026-99-01","2026-02-30","2026-2-01","not-a-date","","0000-01-01","2026-11-03T12:00:00")) {
            mvc.perform(get(URL).param("history_date",value)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("INVALID_INBOX_PAGE"));
        }
        verifyNoInteractions(reads,stores,orders,kitchen);
    }

    @Test void historyCursorRequiresExplicitHistoryDateBeforeAnyRead() throws Exception {
        mvc.perform(get(URL).param("history_before_id","1")).andExpect(status().isBadRequest());
        verifyNoInteractions(reads,stores,orders,kitchen);
    }

    @Test void permissionDeniedPrecedesMalformedParameterParsingAndAllReaders() throws Exception {
        when(auth.requireFrontdeskAccessForStore(1L,Capability.ORDER_VIEW_ACTIVE))
            .thenThrow(new ForbiddenException("Capability required"));
        mvc.perform(get(URL).param("limit","invalid")).andExpect(status().isForbidden());
        verifyNoInteractions(reads,stores,orders,kitchen,access,modules);
    }

    @Test void foreignStoreDeniedPrecedesMalformedParameterParsingAndAllReaders() throws Exception {
        doThrow(new ForbiddenException("Store access denied")).when(access).requireStoreAccess(actor,1L);
        mvc.perform(get(URL).param("history_date","2026-99-01")).andExpect(status().isForbidden());
        verifyNoInteractions(reads,stores,orders,kitchen,modules);
    }

    @Test void unauthenticatedRequestRetainsExisting401WithoutDataReads() throws Exception {
        when(auth.requireFrontdeskAccessForStore(1L,Capability.ORDER_VIEW_ACTIVE))
            .thenThrow(new UnauthorizedException("Authentication required"));
        mvc.perform(get(URL).param("attention_before_id","-1")).andExpect(status().isUnauthorized());
        verifyNoInteractions(reads,stores,orders,kitchen,access,modules);
    }

    @Test void validBoundaryCursorAndDateReachOnlyScopedBuckets() throws Exception {
        when(stores.findById(1L)).thenReturn(Optional.empty());
        when(reads.page(eq("sandbox"),eq(1L),any(),anyString(),nullable(Long.class),anyInt()))
            .thenReturn(new UberEatsInboxReadRepository.Page(List.of(),false,null));
        when(kitchen.states(anyList())).thenReturn(Map.of());
        mvc.perform(get(URL).param("limit","200").param("today_before_id",Long.toString(Long.MAX_VALUE))
                .param("attention_before_id","1").param("history_date","2026-11-03").param("history_before_id","2"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.history.has_more").value(false));
        verify(reads).page(eq("sandbox"),eq(1L),any(),eq("today"),eq(Long.MAX_VALUE),eq(200));
        verify(reads).page(eq("sandbox"),eq(1L),any(),eq("attention"),eq(1L),eq(200));
        verify(reads).page(eq("sandbox"),eq(1L),any(),eq("history"),eq(2L),eq(200));
        verify(auth).requireFrontdeskAccessForStore(1L,Capability.ORDER_VIEW_ACTIVE);
        verify(access).requireStoreAccess(actor,1L);
    }
}
