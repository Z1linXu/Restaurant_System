package com.restaurant.system.common.auth;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.restaurant.system.menu.repository.*;
import com.restaurant.system.owner.master.*;
import com.restaurant.system.owner.provisioning.StoreMenuMasterMappingRepository;
import com.restaurant.system.platform.controller.PlatformResourceAuthorization;
import com.restaurant.system.platform.repository.*;
import com.restaurant.system.platform.service.impl.PlatformAdminServiceImpl;
import com.restaurant.system.station.repository.*;
import com.restaurant.system.user.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.*;

/** Deterministic OSIV race boundary: state changes after controller check, before locked refresh. */
class UserMutationRefreshTest {
    @AfterEach void clear() { RequestContextHolder.resetRequestAttributes(); }
    @ParameterizedTest @ValueSource(strings={"moved-store", "dormant-org", "platform-role"})
    void genericSaveRechecksFreshIdentityAfterLockBeforeWriting(String concurrentChange) {
        var f = new CommercialSecurityFixture();
        var request = new MockHttpServletRequest();
        request.setAttribute(RequestUserContextService.AUTHENTICATED_USER_ATTRIBUTE, f.actor(1));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        var auth = new AuthorizationService(f.context,f.capabilities,f.access,
            mock(com.restaurant.system.order.repository.OrderRepository.class),
            mock(com.restaurant.system.order.repository.OrderItemRepository.class),
            mock(com.restaurant.system.kitchen.repository.KitchenTaskRepository.class));
        var guard = new PlatformResourceAuthorization(auth,f.context,f.access,
            new OwnerOrganizationAuthorizationService(f.orgMembers,f.organizations,f.stores),f.stores,
            mock(RestaurantTemplateRepository.class),mock(StationRepository.class),mock(DiningTableRepository.class),
            mock(MenuCategoryRepository.class),mock(MenuItemRepository.class),mock(MenuItemOptionRepository.class),
            mock(StoreKdsDisplayConfigRepository.class),f.users,f.roles);
        var em = mock(EntityManager.class);
        var service = new PlatformAdminServiceImpl(f.organizations,mock(RestaurantTemplateRepository.class),f.stores,
            mock(StationRepository.class),mock(DiningTableRepository.class),mock(MenuCategoryRepository.class),
            mock(MenuItemRepository.class),mock(MenuItemOptionRepository.class),mock(StoreKdsDisplayConfigRepository.class),
            f.users,f.roles,mock(com.restaurant.system.menu.service.MenuRevisionService.class),
            mock(ChainMasterMenuRepository.class),mock(ChainMasterMenuVersionRepository.class),
            mock(StoreMenuMasterMappingRepository.class),guard,em);
        var body = new User(); body.setId(4L); body.setStore_id(10L);
        body.setRole_id(f.roleRows.values().stream().filter(r -> "OWNER".equals(r.getCode())).findFirst().orElseThrow().getId());
        assertThatCode(() -> guard.user(4L,body)).doesNotThrowAnyException();
        doAnswer(call -> {
            User fresh=call.getArgument(0);
            if (concurrentChange.equals("moved-store")) fresh.setStore_id(20L);
            else if (concurrentChange.equals("dormant-org")) f.orgMember(4,200,"OWNER",true);
            else fresh.setRole_id(f.roleRows.values().stream().filter(r -> "ADMIN".equals(r.getCode())).findFirst().orElseThrow().getId());
            return null;
        }).when(em).refresh(f.userRows.get(4L),LockModeType.PESSIMISTIC_WRITE);
        assertThatThrownBy(() -> service.saveUser(body)).isInstanceOf(ForbiddenException.class);
        verify(f.users,never()).save(any());
        var ordered = inOrder(f.users,em);
        ordered.verify(f.users).findByIdForUpdate(4L);
        ordered.verify(em).refresh(f.userRows.get(4L),LockModeType.PESSIMISTIC_WRITE);
        ordered.verify(f.users).findById(4L);
    }
}
