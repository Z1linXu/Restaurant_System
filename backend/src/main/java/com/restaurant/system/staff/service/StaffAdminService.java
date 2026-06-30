package com.restaurant.system.staff.service;

import com.restaurant.system.common.auth.AuthenticatedUser;
import com.restaurant.system.staff.dto.ResetPasswordRequest;
import com.restaurant.system.staff.dto.StaffStoreResponse;
import com.restaurant.system.staff.dto.StaffUserRequest;
import com.restaurant.system.staff.dto.StaffUserResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

public interface StaffAdminService {

    List<StaffStoreResponse> getAccessibleStores(AuthenticatedUser actor);

    List<StaffUserResponse> getStaff(Long storeId, AuthenticatedUser actor);

    StaffUserResponse createStaff(StaffUserRequest request, AuthenticatedUser actor, HttpServletRequest servletRequest);

    StaffUserResponse updateStaff(Long userId, StaffUserRequest request, AuthenticatedUser actor, HttpServletRequest servletRequest);

    StaffUserResponse deactivateStaff(Long userId, AuthenticatedUser actor, HttpServletRequest servletRequest);

    StaffUserResponse reactivateStaff(Long userId, AuthenticatedUser actor, HttpServletRequest servletRequest);

    StaffUserResponse resetPassword(Long userId, ResetPasswordRequest request, AuthenticatedUser actor, HttpServletRequest servletRequest);
}
