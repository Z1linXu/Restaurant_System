package com.restaurant.system.merchant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.audit.service.AuditLogService;
import com.restaurant.system.common.auth.*;
import com.restaurant.system.menu.service.MenuRevisionService;
import com.restaurant.system.owner.exception.OwnerStoreProvisioningException;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.repository.StoreRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MerchantConfigurationServiceTest {
    final StoreMerchantConfigurationRepository configs = mock(StoreMerchantConfigurationRepository.class);
    final StoreRepository stores = mock(StoreRepository.class);
    final AuthorizationService auth = mock(AuthorizationService.class);
    final MenuRevisionService revisions = mock(MenuRevisionService.class);
    final AuditLogService audit = mock(AuditLogService.class);
    final ObjectMapper json = new ObjectMapper();
    final AuthenticatedUser owner = new AuthenticatedUser(7L, 3L, 1L, "synthetic", "Synthetic", "OWNER");
    MerchantConfigurationService service;
    Store store;
    @BeforeEach void setup() {
        service = new MerchantConfigurationService(configs, stores, auth, revisions, audit, "America/Toronto");
        store = new Store(); store.id = 3L; store.organization_id = 11L; store.name = "Synthetic Neutral";
        when(stores.findById(3L)).thenReturn(Optional.of(store));
        when(stores.findByIdForUpdate(3L)).thenReturn(Optional.of(store));
        when(auth.requireForStore(3L, Capability.ADMIN_STORE_CONFIG)).thenReturn(owner);
        when(auth.isOwner(owner)).thenReturn(true);
    }
    @Test void missingIsNeutralEvenWithLegacyNameAndMarker() {
        store.provisioning_source = "LEGACY_EXISTING_STORE"; store.provisioned_profile_code = "ST_DENIS_CANONICAL_PROFILE";
        assertThat(service.isLanzhouCompatible(3L)).isFalse();
        assertThat(service.safeSummary(3L).profile_code()).isEqualTo("NEUTRAL_RESTAURANT");
        assertThat(service.safeSummary(3L).config_revision()).isZero();
        verifyNoInteractions(audit, revisions);
    }
    @Test void onlyExactValidPersistedCompatibilityWorks() {
        service.initializeForProvisionedStore(store, 7L, "ST_DENIS_CANONICAL_PROFILE", "v2");
        var argument = org.mockito.ArgumentCaptor.forClass(StoreMerchantConfiguration.class);
        verify(configs).save(argument.capture()); var c = argument.getValue();
        when(configs.findById(3L)).thenReturn(Optional.of(c));
        assertThat(service.isLanzhouCompatible(3L)).isTrue();
        c.schema_version = 2; assertThat(service.isLanzhouCompatible(3L)).isFalse();
        c.schema_version = 1; c.profile_version = "v1"; assertThat(service.isLanzhouCompatible(3L)).isFalse();
        c.profile_version = "v2"; c.compatibility_mode = "NEUTRAL"; assertThat(service.isLanzhouCompatible(3L)).isFalse();
    }
    @Test void strictWriteRejectsUnknownTypesAndAllForeignResourceReferences() throws Exception {
        String valid = "{\"schema_version\":1,\"expected_config_revision\":0,\"brand_name\":\"New\",\"locale\":\"fr-CA\"}";
        for (String field : new String[]{"store_id", "organization_id", "station_id", "table_id", "printer_id", "device_id", "menu_item_id", "option_id", "combo_id", "resource_references", "profile_code", "timezone", "currency", "tax_policy_reference"}) {
            var body = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(valid); body.put(field, 99);
            assertThatThrownBy(() -> service.update(3L, body, null)).isInstanceOf(OwnerStoreProvisioningException.class);
        }
        for (String bad : new String[]{valid.replace("\"schema_version\":1", "\"schema_version\":\"1\""),
            valid.replace("\"schema_version\":1", "\"schema_version\":2"), valid.replace(":0,", ":0.5,"),
            valid.replace("fr-CA", "not a locale"), valid.replace("\"New\"", "false")}) {
            var body = json.readTree(bad);
            assertThatThrownBy(() -> service.update(3L, body, null)).isInstanceOf(OwnerStoreProvisioningException.class);
        }
        verify(stores, never()).findByIdForUpdate(any()); verify(configs, never()).saveAndFlush(any());
    }
    @Test void fullWriteUsesRevisionAuditAndMenuInvalidation() throws Exception {
        var body = json.readTree("{\"schema_version\":1,\"expected_config_revision\":0,\"brand_name\":\"New\",\"locale\":\"fr-CA\"}");
        var result = service.update(3L, body, null);
        var argument = org.mockito.ArgumentCaptor.forClass(StoreMerchantConfiguration.class);
        verify(configs).saveAndFlush(argument.capture());
        assertThat(argument.getValue().config_revision).isEqualTo(1L);
        assertThat(argument.getValue().profile_code).isEqualTo("NEUTRAL_RESTAURANT");
        verify(auth, times(2)).requireForStore(3L, Capability.ADMIN_STORE_CONFIG);
        verify(audit).record(eq(3L), eq(owner), eq("MERCHANT_CONFIGURATION_UPDATED"), anyString(), eq(3L), anyString(), anyMap(), isNull());
        verify(revisions).incrementRevision(3L);
        assertThat(result.resource_references()).allMatch(r -> r.api_path().contains("3"));
    }
    @Test void staleRevisionAndRevocationAfterLockCannotWrite() throws Exception {
        var body = json.readTree("{\"schema_version\":1,\"expected_config_revision\":4,\"brand_name\":\"New\",\"locale\":\"en-CA\"}");
        assertThatThrownBy(() -> service.update(3L, body, null)).hasMessageContaining("reload");
        when(auth.requireForStore(3L, Capability.ADMIN_STORE_CONFIG)).thenReturn(owner).thenThrow(new ForbiddenException("revoked"));
        assertThatThrownBy(() -> service.update(3L, body, null)).isInstanceOf(ForbiddenException.class);
        verify(configs, never()).saveAndFlush(any()); verifyNoInteractions(audit, revisions);
    }
    @Test void nonOwnerAndForeignStoreCannotReadConfigurationOrPointers() {
        when(auth.isOwner(owner)).thenReturn(false);
        assertThatThrownBy(() -> service.get(3L)).isInstanceOf(ForbiddenException.class);
        when(auth.requireForStore(99L, Capability.ADMIN_STORE_CONFIG)).thenThrow(new ForbiddenException("foreign"));
        assertThatThrownBy(() -> service.get(99L)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(configs);
    }
}
