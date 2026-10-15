package com.restaurant.system.common.auth;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;

class StoreAccessServiceTest {
    final CommercialSecurityFixture f=new CommercialSecurityFixture();
    @Test void ownerOnlyInheritsExactActiveOwnerOrganization() {
        assertThat(f.access.accessibleStores(f.actor(1))).extracting(s -> s.id).containsExactly(10L,11L);
        assertThat(f.access.canAccessStore(f.actor(1),20L)).isFalse();
        f.orgMember(1,100,"MANAGER",true);
        assertThat(f.access.canAccessStore(f.actor(1),11L)).isFalse();
        f.orgMember(1,100,"OWNER",false);
        assertThat(f.access.canAccessStore(f.actor(1),10L)).isFalse();
    }
    @Test void storeStaffCannotReadSiblingOrForeignStores() {
        assertThat(f.access.canAccessStore(f.actor(4),10L)).isTrue();
        assertThat(f.access.canAccessStore(f.actor(4),11L)).isFalse();
        assertThat(f.access.canAccessStore(f.actor(4),20L)).isFalse();
    }
    @Test void adminCannotUseEvenAnExplicitMerchantMembershipAsSupportGrant() {
        f.member(5,10,"OWNER",true);f.orgMember(5,100,"OWNER",true);
        assertThat(f.access.canAccessStore(f.actor(5),10L)).isFalse();
        assertThat(f.access.accessibleStores(f.actor(5))).isEmpty();
        assertThat(f.access.accessibleOrganizations(f.actor(5))).isEmpty();
    }
    @Test void legacyOnlyAppliesToNeverMemberAndCannotRestoreRevokedDefault() {
        assertThat(f.access.canAccessStore(f.actor(6),10L)).isTrue();
        assertThat(f.access.canAccessStore(f.actor(6),11L)).isFalse();
        assertThat(f.access.canAccessStore(f.actor(9),10L)).isFalse();
        assertThat(f.access.accessibleStores(f.actor(9))).isEmpty();
    }
    @Test void effectiveRoleCannotExceedEitherGlobalOrMembershipPreset() {
        assertThat(f.access.forStore(f.actor(7),11L).roleCode()).isEqualTo("FRONTDESK");
        assertThat(f.access.forStore(f.actor(8),10L).roleCode()).isEqualTo("FRONTDESK");
        f.member(4,10,"HOT_KITCHEN",true);
        assertThat(f.access.canAccessStore(f.actor(4),10L)).isFalse();
    }
    @Test void malformedMembershipAndInactiveOrganizationDoNotGrantAccess() {
        f.membershipRows.get("4:10").organizationId=200L;
        assertThat(f.access.canAccessStore(f.actor(4),10L)).isFalse();
        f.organizationRows.get(100L).status="inactive";
        assertThat(f.access.canAccessStore(f.actor(1),11L)).isFalse();
        assertThat(f.access.canAccessStore(f.actor(1),999L)).isFalse();
    }
}
