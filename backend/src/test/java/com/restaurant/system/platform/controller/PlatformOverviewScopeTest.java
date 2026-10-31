package com.restaurant.system.platform.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.audit.service.AuditLogService;
import com.restaurant.system.common.auth.*;
import com.restaurant.system.common.feature.FeatureFlagService;
import com.restaurant.system.modules.StoreModuleAccessEvaluator;
import com.restaurant.system.platform.dto.PlatformAdminOverviewResponse;
import com.restaurant.system.platform.entity.Organization;
import com.restaurant.system.platform.entity.RestaurantTemplate;
import com.restaurant.system.platform.service.PlatformAdminService;
import com.restaurant.system.user.entity.Store;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PlatformOverviewScopeTest {
 private final PlatformAdminService service=mock(PlatformAdminService.class);
 private final AuthorizationService auth=mock(AuthorizationService.class);
 private final StoreAccessService access=mock(StoreAccessService.class);
 private final PlatformAdminController controller=new PlatformAdminController(service,auth,mock(FeatureFlagService.class),mock(AuditLogService.class),mock(StoreModuleAccessEvaluator.class));
 private PlatformAdminOverviewResponse data;
 private AuthenticatedUser user;
 private Store own,secondOwn,foreign;
 private Organization org,foreignOrg;
 @BeforeEach void setup(){
  controller.setOverviewScope(access,new RoleCapabilityRegistry());
  own=store(1,1);secondOwn=store(3,1);foreign=store(2,2);org=org(1);foreignOrg=org(2);
  data=new PlatformAdminOverviewResponse();data.stores=List.of(own,secondOwn,foreign);data.organizations=List.of(org,foreignOrg);
  data.templates=List.of(template(1,1L,1L,"ALLOWED"),template(2,2L,2L,"FOREIGN_SECRET"),
   template(3,1L,2L,"FOREIGN_SOURCE_SECRET"),template(4,null,null,"GLOBAL_UNAPPROVED_SECRET"));
  when(service.getOverview(1L)).thenReturn(data);
 }
 private void authenticate(String role,List<Store> stores){
  user=new AuthenticatedUser(10L,1L,1L,"synthetic","SYNTHETIC",role);
  when(auth.requireForStore(1L,Capability.ADMIN_STORE_CONFIG,Capability.ADMIN_USER_ROLE_MANAGE)).thenReturn(user);
  when(access.accessibleStores(user)).thenReturn(stores);when(access.accessibleOrganizations(user)).thenReturn(List.of(org));
 }
 @ParameterizedTest @ValueSource(strings={"OWNER","MANAGER"})
 void ownerAndManagerCannotReceiveForeignMetadataOrTemplateConfiguration(String role)throws Exception{
  authenticate(role,List.of(own));var out=controller.getOverview(1L).getData();
  assertEquals(List.of(own),out.stores);assertEquals(List.of(org),out.organizations);
  assertEquals(List.of(1L),out.templates.stream().map(t->t.id).toList());
  String payload=new ObjectMapper().findAndRegisterModules().writeValueAsString(out);assertTrue(payload.contains("ALLOWED"));assertFalse(payload.contains("SECRET"));
 }
 @Test void existingAuthorizedMultipleStoresInSameOrganizationRemainVisible(){
  authenticate("OWNER",List.of(own,secondOwn));var out=controller.getOverview(1L).getData();assertEquals(List.of(own,secondOwn),out.stores);
 }
 @Test void platformAdminRetainsItsExistingGlobalView(){
  authenticate("ADMIN",List.of());var out=controller.getOverview(1L).getData();assertEquals(3,out.stores.size());assertEquals(2,out.organizations.size());assertEquals(4,out.templates.size());verifyNoInteractions(access);
 }
 @Test void deniedRequestedStoreDoesNotReadAnyOverview(){
  when(auth.requireForStore(2L,Capability.ADMIN_STORE_CONFIG,Capability.ADMIN_USER_ROLE_MANAGE)).thenThrow(new ForbiddenException("Denied"));
  assertThrows(ForbiddenException.class,()->controller.getOverview(2L));verifyNoInteractions(service,access);
 }
 @Test void ownedTemplateWithoutSourceStoreRemainsVisible(){
  authenticate("OWNER",List.of(own));data.templates=List.of(template(5,1L,null,"ALLOWED_OWN_ORG"));assertEquals(1,controller.getOverview(1L).getData().templates.size());
 }
 private Store store(long id,long org){var s=new Store();s.id=id;s.organization_id=org;return s;}
 private Organization org(long id){var o=new Organization();o.id=id;o.code="SYNTHETIC_ORG_"+id;return o;}
 private RestaurantTemplate template(long id,Long org,Long source,String marker){var t=new RestaurantTemplate();t.id=id;t.organization_id=org;t.source_store_id=source;t.default_role_setup_json="{\"marker\":\""+marker+"\"}";return t;}
}
