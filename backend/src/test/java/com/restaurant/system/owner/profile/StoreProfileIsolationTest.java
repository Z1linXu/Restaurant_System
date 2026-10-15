package com.restaurant.system.owner.profile;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.restaurant.system.common.auth.AuthenticatedUser;
import com.restaurant.system.common.auth.ForbiddenException;
import com.restaurant.system.platform.entity.Organization;
import com.restaurant.system.platform.repository.OrganizationRepository;
import com.restaurant.system.user.entity.OrganizationMembership;
import com.restaurant.system.user.repository.OrganizationMembershipRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StoreProfileIsolationTest {
    final OrganizationMembershipRepository memberships = mock(OrganizationMembershipRepository.class);
    final OrganizationRepository organizations = mock(OrganizationRepository.class);
    final StoreProfileRepository profiles = mock(StoreProfileRepository.class);
    final StoreProfileVersionRepository versions = mock(StoreProfileVersionRepository.class);
    final StoreProfileArtifactRepository artifacts = mock(StoreProfileArtifactRepository.class);
    final StoreProfileAccessService access = new StoreProfileAccessService(memberships, organizations);
    final StoreProfileCatalogService catalog = new StoreProfileCatalogService(profiles, versions, artifacts,
        new StoreProfileContractValidator(), access);

    AuthenticatedUser owner(long user, long org) {
        var m = new OrganizationMembership(); m.userId = user; m.organizationId = org;
        m.roleCode = "OWNER"; m.isActive = true;
        var organization = new Organization(); organization.id = org; organization.status = "active";
        when(memberships.findAllByUserIdAndIsActiveTrueOrderByOrganizationIdAsc(user)).thenReturn(List.of(m));
        lenient().when(organizations.findById(org)).thenReturn(Optional.of(organization));
        return new AuthenticatedUser(user, null, 1L, "synthetic", "Synthetic", "OWNER");
    }

    StoreProfileEntity profile(String code, Long org) {
        var p = new StoreProfileEntity(); p.id = (long) code.hashCode(); p.profile_code = code;
        p.owner_organization_id = org; return p;
    }

    @Test void foreignPrivateRootIsAbsentAndGuessedVersionsNeverReadArtifacts() {
        var a = owner(11, 101); var b = owner(22, 202); var privateA = profile("PRIVATE_A", 101L);
        var publicPreset = profile("SAFE_PUBLIC", null); publicPreset.visibility = "PUBLIC_PRESET"; publicPreset.status = "PUBLISHED";
        when(profiles.findAllByOrderByProfileCodeAsc()).thenReturn(List.of(privateA, publicPreset));
        when(profiles.findByProfileCode("PRIVATE_A")).thenReturn(Optional.of(privateA));
        when(versions.findAllByProfileIdOrderByProfileVersionAsc(anyLong())).thenReturn(List.of());
        assertThat(catalog.listProfiles(a)).extracting(r -> r.profileCode()).containsExactly("PRIVATE_A", "SAFE_PUBLIC");
        assertThat(catalog.listProfiles(b)).extracting(r -> r.profileCode()).containsExactly("SAFE_PUBLIC");
        assertThatThrownBy(() -> catalog.getVersion(b, "PRIVATE_A", "guessed-v1"))
            .isInstanceOf(ForbiddenException.class).hasMessage("Store Profile is not available");
        verify(versions, never()).findByProfileIdAndProfileVersion(anyLong(), anyString());
        verifyNoInteractions(artifacts);
        assertThatCode(() -> StoreProfileAccessService.requireUse(privateA, 101L)).doesNotThrowAnyException();
        assertThatThrownBy(() -> StoreProfileAccessService.requireUse(privateA, 202L)).isInstanceOf(ForbiddenException.class);
    }

    @Test void unknownOwnershipAdminRevocationAndInvalidPublicOwnerFailClosed() {
        var a = owner(11, 101); var privateA = profile("PRIVATE_A", 101L);
        assertThat(access.canRead(a, privateA)).isTrue();
        var admin = new AuthenticatedUser(11L, null, 1L, "operator", "Operator", "ADMIN");
        assertThat(access.canRead(admin, privateA)).isFalse();
        assertThat(access.canRead(a, profile("UNKNOWN", null))).isFalse();
        privateA.visibility = "PUBLIC_PRESET";
        assertThat(access.canRead(a, privateA)).isFalse();
        privateA.visibility = "PRIVATE";
        when(memberships.findAllByUserIdAndIsActiveTrueOrderByOrganizationIdAsc(11L)).thenReturn(List.of());
        assertThat(access.canRead(a, privateA)).isFalse();
    }

    @Test void creationCatalogDoesNotExposePrivateDefaultsToOtherOrganizations() {
        var privateA = profile("ST_DENIS_CANONICAL_PROFILE", 101L);
        when(profiles.findByProfileCode(privateA.profile_code)).thenReturn(Optional.of(privateA));
        var creation = new com.restaurant.system.owner.provisioning.OwnerStoreProvisioningCatalogService(profiles);
        assertThat(creation.catalog(101L).enabled).isTrue();
        var denied = creation.catalog(202L);
        assertThat(denied.enabled).isFalse(); assertThat(denied.profile_code).isNull();
        assertThat(denied.master_menu_key).isNull();
    }
}
