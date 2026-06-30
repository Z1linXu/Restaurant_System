package com.restaurant.system.staff.controller;

import com.restaurant.system.common.auth.AuthorizationService;
import com.restaurant.system.common.auth.Capability;
import com.restaurant.system.common.auth.RequestUserContextService;
import com.restaurant.system.common.response.ApiResponse;
import com.restaurant.system.staff.dto.ResetPasswordRequest;
import com.restaurant.system.staff.dto.StaffStoreResponse;
import com.restaurant.system.staff.dto.StaffUserRequest;
import com.restaurant.system.staff.dto.StaffUserResponse;
import com.restaurant.system.staff.service.StaffAdminService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/staff")
public class StaffAdminController {

    private final StaffAdminService staffAdminService;
    private final AuthorizationService authorizationService;
    private final RequestUserContextService requestUserContextService;

    public StaffAdminController(
        StaffAdminService staffAdminService,
        AuthorizationService authorizationService,
        RequestUserContextService requestUserContextService
    ) {
        this.staffAdminService = staffAdminService;
        this.authorizationService = authorizationService;
        this.requestUserContextService = requestUserContextService;
    }

    @GetMapping("/stores")
    public ApiResponse<List<StaffStoreResponse>> getAccessibleStores() {
        var user = authorizationService.require(Capability.ADMIN_USER_ROLE_MANAGE);
        return ApiResponse.success(staffAdminService.getAccessibleStores(user));
    }

    @GetMapping
    public ApiResponse<List<StaffUserResponse>> getStaff(@RequestParam Long store_id) {
        var user = authorizationService.requireStaffManageForStore(store_id);
        return ApiResponse.success(staffAdminService.getStaff(store_id, user));
    }

    @PostMapping
    public ApiResponse<StaffUserResponse> createStaff(@Valid @RequestBody StaffUserRequest request, HttpServletRequest servletRequest) {
        var user = requestUserContextService.getRequiredUser();
        return ApiResponse.success("Staff created", staffAdminService.createStaff(request, user, servletRequest));
    }

    @PutMapping("/{userId}")
    public ApiResponse<StaffUserResponse> updateStaff(
        @PathVariable Long userId,
        @Valid @RequestBody StaffUserRequest request,
        HttpServletRequest servletRequest
    ) {
        var user = requestUserContextService.getRequiredUser();
        return ApiResponse.success("Staff updated", staffAdminService.updateStaff(userId, request, user, servletRequest));
    }

    @PostMapping("/{userId}/deactivate")
    public ApiResponse<StaffUserResponse> deactivateStaff(@PathVariable Long userId, HttpServletRequest servletRequest) {
        var user = requestUserContextService.getRequiredUser();
        return ApiResponse.success("Staff deactivated", staffAdminService.deactivateStaff(userId, user, servletRequest));
    }

    @PostMapping("/{userId}/reactivate")
    public ApiResponse<StaffUserResponse> reactivateStaff(@PathVariable Long userId, HttpServletRequest servletRequest) {
        var user = requestUserContextService.getRequiredUser();
        return ApiResponse.success("Staff reactivated", staffAdminService.reactivateStaff(userId, user, servletRequest));
    }

    @PostMapping("/{userId}/reset-password")
    public ApiResponse<StaffUserResponse> resetPassword(
        @PathVariable Long userId,
        @Valid @RequestBody ResetPasswordRequest request,
        HttpServletRequest servletRequest
    ) {
        var user = requestUserContextService.getRequiredUser();
        return ApiResponse.success("Password reset", staffAdminService.resetPassword(userId, request, user, servletRequest));
    }
}
