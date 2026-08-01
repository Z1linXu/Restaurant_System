package com.restaurant.system.menu.service;

import com.restaurant.system.menu.dto.MenuRevisionResponse;
import com.restaurant.system.user.entity.Store;
import java.util.Collection;
import java.util.List;

public interface MenuRevisionService {

    String CATALOG_VERSION = "menu-catalog-v3";
    String TAX_POLICY_VERSION = "ca-qc-tax-2026-01";

    MenuRevisionResponse getRevision(Long storeId);

    void incrementRevision(Long storeId);

    void incrementRevisionsInOrder(Collection<Long> storeIds);

    List<Store> lockStoresInOrder(Collection<Long> storeIds);
}
