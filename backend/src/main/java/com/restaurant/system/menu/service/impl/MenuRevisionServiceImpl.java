package com.restaurant.system.menu.service.impl;

import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.menu.dto.MenuRevisionResponse;
import com.restaurant.system.menu.service.MenuRevisionService;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.repository.StoreRepository;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MenuRevisionServiceImpl implements MenuRevisionService {

    private final StoreRepository storeRepository;

    public MenuRevisionServiceImpl(StoreRepository storeRepository) {
        this.storeRepository = storeRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public MenuRevisionResponse getRevision(Long storeId) {
        Store store = loadStore(storeId);
        long revision = store.menu_revision == null ? 1L : store.menu_revision;
        LocalDateTime updatedAt = store.menu_updated_at == null
            ? (store.updated_at == null ? LocalDateTime.now() : store.updated_at)
            : store.menu_updated_at;
        return new MenuRevisionResponse(
            store.id,
            store.organization_id,
            revision,
            updatedAt,
            CATALOG_VERSION,
            TAX_POLICY_VERSION,
            buildEtag(store.id, revision)
        );
    }

    @Override
    @Transactional
    public void incrementRevision(Long storeId) {
        if (storeId == null || storeRepository.incrementMenuRevision(storeId) != 1) {
            throw new BusinessException("Store not found for menu revision: " + storeId);
        }
    }

    private Store loadStore(Long storeId) {
        if (storeId == null) {
            throw new BusinessException("Store id is required");
        }
        return storeRepository.findById(storeId)
            .orElseThrow(() -> new BusinessException("Store not found: " + storeId));
    }

    private String buildEtag(Long storeId, long revision) {
        return "menu-" + storeId + "-" + revision + "-" + CATALOG_VERSION + "-" + TAX_POLICY_VERSION;
    }
}
