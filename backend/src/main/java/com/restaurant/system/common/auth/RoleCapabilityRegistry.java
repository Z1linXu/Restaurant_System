package com.restaurant.system.common.auth;

import java.util.EnumSet;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class RoleCapabilityRegistry {

    private static final String ROLE_FRONTDESK = "FRONTDESK";
    private static final String ROLE_OWNER = "OWNER";
    private static final String ROLE_MANAGER = "MANAGER";
    private static final String ROLE_HOT_KITCHEN = "HOT_KITCHEN";
    private static final String ROLE_NOODLE_VIEW = "NOODLE_VIEW";
    private static final String ROLE_PASS = "PASS";
    private static final String ROLE_ADMIN = "ADMIN";

    private static final Map<String, Set<Capability>> ROLE_CAPABILITIES = Map.of(
        ROLE_OWNER,
        EnumSet.allOf(Capability.class),
        ROLE_MANAGER,
        EnumSet.of(
            Capability.ORDER_CREATE,
            Capability.ORDER_EDIT_DRAFT,
            Capability.ORDER_SUBMIT,
            Capability.ORDER_MODIFY_SUBMITTED,
            Capability.ORDER_COMPLETE,
            Capability.ORDER_CANCEL,
            Capability.ORDER_VIEW_ACTIVE,
            Capability.ORDER_VIEW_HISTORY,
            Capability.ORDER_VIEW_DETAIL,
            Capability.BEVERAGE_VIEW_BOARD,
            Capability.BEVERAGE_START,
            Capability.BEVERAGE_READY,
            Capability.BEVERAGE_SERVED,
            Capability.BEVERAGE_CANCEL,
            Capability.SHELF_VIEW,
            Capability.SHELF_SERVED,
            Capability.ADMIN_MENU_MANAGE,
            Capability.ADMIN_STORE_CONFIG,
            Capability.ADMIN_HISTORY_LIMIT,
            Capability.ADMIN_USER_ROLE_MANAGE
        ),
        ROLE_FRONTDESK,
        EnumSet.of(
            Capability.ORDER_CREATE,
            Capability.ORDER_EDIT_DRAFT,
            Capability.ORDER_SUBMIT,
            Capability.ORDER_MODIFY_SUBMITTED,
            Capability.ORDER_COMPLETE,
            Capability.ORDER_CANCEL,
            Capability.ORDER_VIEW_ACTIVE,
            Capability.ORDER_VIEW_HISTORY,
            Capability.ORDER_VIEW_DETAIL,
            Capability.BEVERAGE_VIEW_BOARD,
            Capability.BEVERAGE_START,
            Capability.BEVERAGE_READY,
            Capability.BEVERAGE_SERVED,
            Capability.BEVERAGE_CANCEL,
            Capability.SHELF_VIEW,
            Capability.SHELF_SERVED
        ),
        ROLE_HOT_KITCHEN,
        EnumSet.of(
            Capability.KDS_HOT_VIEW,
            Capability.KDS_HOT_START,
            Capability.KDS_HOT_READY_FOR_PICKUP
        ),
        ROLE_NOODLE_VIEW,
        EnumSet.of(Capability.KDS_NOODLE_VIEW),
        ROLE_PASS,
        EnumSet.of(
            Capability.KDS_PASS_VIEW,
            Capability.KDS_PASS_READY_FOR_PICKUP,
            Capability.SHELF_VIEW
        ),
        ROLE_ADMIN,
        EnumSet.allOf(Capability.class)
    );

    public boolean hasCapability(String roleCode, Capability capability) {
        if (roleCode == null) {
            return false;
        }
        Set<Capability> capabilities = ROLE_CAPABILITIES.get(roleCode.toUpperCase());
        return capabilities != null && capabilities.contains(capability);
    }

    public Set<Capability> getCapabilities(String roleCode) {
        if (roleCode == null) {
            return Collections.emptySet();
        }
        Set<Capability> capabilities = ROLE_CAPABILITIES.get(roleCode.toUpperCase());
        return capabilities == null ? Collections.emptySet() : Set.copyOf(capabilities);
    }

    public boolean isAdmin(String roleCode) {
        return ROLE_ADMIN.equalsIgnoreCase(roleCode);
    }

    public boolean isOwner(String roleCode) {
        return ROLE_OWNER.equalsIgnoreCase(roleCode) || ROLE_ADMIN.equalsIgnoreCase(roleCode);
    }

    public boolean isManager(String roleCode) {
        return ROLE_MANAGER.equalsIgnoreCase(roleCode);
    }

    public boolean isFrontdesk(String roleCode) {
        return ROLE_FRONTDESK.equalsIgnoreCase(roleCode);
    }
}
