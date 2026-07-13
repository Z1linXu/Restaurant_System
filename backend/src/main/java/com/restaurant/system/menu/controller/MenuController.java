package com.restaurant.system.menu.controller;

import com.restaurant.system.common.auth.AuthorizationService;
import com.restaurant.system.common.auth.Capability;
import com.restaurant.system.common.response.ApiResponse;
import com.restaurant.system.menu.dto.MenuCatalogResponse;
import com.restaurant.system.menu.dto.MenuRevisionResponse;
import com.restaurant.system.menu.service.MenuService;
import com.restaurant.system.menu.service.MenuRevisionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/menu")
public class MenuController {

    private final MenuService menuService;
    private final MenuRevisionService menuRevisionService;
    private final AuthorizationService authorizationService;

    public MenuController(
        MenuService menuService,
        MenuRevisionService menuRevisionService,
        AuthorizationService authorizationService
    ) {
        this.menuService = menuService;
        this.menuRevisionService = menuRevisionService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/health")
    public ApiResponse<String> health() {
        return ApiResponse.success("menu module ready");
    }

    @GetMapping("/catalog")
    public ApiResponse<MenuCatalogResponse> getCatalog(@RequestParam("store_id") Long storeId) {
        authorizationService.requireForStore(storeId, Capability.ORDER_CREATE);
        return ApiResponse.success(menuService.getCatalog(storeId));
    }

    @GetMapping("/catalog/revision")
    public ApiResponse<MenuRevisionResponse> getCatalogRevision(@RequestParam("store_id") Long storeId) {
        authorizationService.requireForStore(storeId, Capability.ORDER_CREATE);
        return ApiResponse.success(menuRevisionService.getRevision(storeId));
    }
}
