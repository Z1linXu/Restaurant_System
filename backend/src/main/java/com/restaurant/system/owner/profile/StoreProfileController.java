package com.restaurant.system.owner.profile;

import com.restaurant.system.common.auth.AuthorizationService;
import com.restaurant.system.common.response.ApiResponse;
import com.restaurant.system.owner.profile.dto.StoreProfileSummaryResponse;
import com.restaurant.system.owner.profile.dto.StoreProfileVersionResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/store-profiles")
public class StoreProfileController {

    private final AuthorizationService authorizationService;
    private final StoreProfileCatalogService catalogService;

    public StoreProfileController(
        AuthorizationService authorizationService,
        StoreProfileCatalogService catalogService
    ) {
        this.authorizationService = authorizationService;
        this.catalogService = catalogService;
    }

    @GetMapping
    public ApiResponse<List<StoreProfileSummaryResponse>> listProfiles() {
        authorizationService.requireOwner();
        return ApiResponse.success(catalogService.listProfiles());
    }

    @GetMapping("/{profileCode}/versions/{profileVersion}")
    public ApiResponse<StoreProfileVersionResponse> getProfileVersion(
        @PathVariable String profileCode,
        @PathVariable String profileVersion
    ) {
        authorizationService.requireOwner();
        return ApiResponse.success(catalogService.getVersion(profileCode, profileVersion));
    }
}
