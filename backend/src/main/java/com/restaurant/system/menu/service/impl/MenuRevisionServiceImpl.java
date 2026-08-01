package com.restaurant.system.menu.service.impl;

import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.menu.dto.MenuRevisionResponse;
import com.restaurant.system.menu.service.MenuRevisionService;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.repository.StoreRepository;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
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
        incrementRevisionsInOrder(List.of(requireStoreId(storeId)));
    }

    @Override
    @Transactional
    public void incrementRevisionsInOrder(Collection<Long> storeIds) {
        List<Store> stores = lockStoresInOrder(storeIds);
        for (Store store : stores) {
            if (storeRepository.incrementMenuRevision(store.id) != 1) {
                throw new BusinessException("Store not found for menu revision: " + store.id);
            }
        }
    }

    @Override
    @Transactional
    public List<Store> lockStoresInOrder(Collection<Long> storeIds) {
        if (storeIds == null || storeIds.isEmpty() || storeIds.stream().anyMatch(id -> id == null)) {
            throw new BusinessException("Store ids are required for menu mutation lock");
        }
        List<Long> orderedIds = storeIds.stream().distinct().sorted().toList();
        List<Store> stores = storeRepository.findAllByIdInForUpdateOrderByIdAsc(orderedIds);
        if (stores.size() != orderedIds.size()
            || !stores.stream().map(store -> store.id).toList().equals(orderedIds)) {
            throw new BusinessException("Store not found for menu mutation lock");
        }
        return stores;
    }

    private Store loadStore(Long storeId) {
        if (storeId == null) {
            throw new BusinessException("Store id is required");
        }
        return storeRepository.findById(storeId)
            .orElseThrow(() -> new BusinessException("Store not found: " + storeId));
    }

    private Long requireStoreId(Long storeId) {
        if (storeId == null) {
            throw new BusinessException("Store id is required for menu revision");
        }
        return storeId;
    }

    private String buildEtag(Long storeId, long revision) {
        return "menu-" + storeId + "-" + revision + "-" + CATALOG_VERSION + "-" + TAX_POLICY_VERSION;
    }
}
