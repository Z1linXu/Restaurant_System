package com.restaurant.system.common.auth;

public enum Capability {
    ORDER_CREATE("order:create"),
    ORDER_EDIT_DRAFT("order:edit_draft"),
    ORDER_SUBMIT("order:submit"),
    ORDER_MODIFY_SUBMITTED("order:modify_submitted"),
    ORDER_COMPLETE("order:complete"),
    ORDER_CANCEL("order:cancel"),
    ORDER_VIEW_ACTIVE("order:view_active"),
    ORDER_VIEW_HISTORY("order:view_history"),
    ORDER_VIEW_DETAIL("order:view_detail"),
    BEVERAGE_VIEW_BOARD("beverage:view_board"),
    BEVERAGE_START("beverage:start"),
    BEVERAGE_READY("beverage:ready"),
    BEVERAGE_SERVED("beverage:served"),
    BEVERAGE_CANCEL("beverage:cancel"),
    SHELF_VIEW("shelf:view"),
    SHELF_SERVED("shelf:served"),
    KDS_HOT_VIEW("kds:hot:view"),
    KDS_HOT_START("kds:hot:start"),
    KDS_HOT_READY_FOR_PICKUP("kds:hot:ready_for_pickup"),
    KDS_NOODLE_VIEW("kds:noodle:view"),
    KDS_PASS_VIEW("kds:pass:view"),
    KDS_PASS_READY_FOR_PICKUP("kds:pass:ready_for_pickup"),
    ADMIN_STORE_CONFIG("admin:store_config"),
    ADMIN_HISTORY_LIMIT("admin:history_limit"),
    ADMIN_USER_ROLE_MANAGE("admin:user_role_manage");

    private final String code;

    Capability(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
