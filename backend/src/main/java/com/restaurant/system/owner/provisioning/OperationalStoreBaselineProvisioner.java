package com.restaurant.system.owner.provisioning;

import com.restaurant.system.common.auth.AuthenticatedUser;
import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.owner.provisioning.part2.StoreLogicalPrinterRoleEntity;
import com.restaurant.system.owner.provisioning.part2.StoreLogicalPrinterRoleRepository;
import com.restaurant.system.station.entity.DiningTable;
import com.restaurant.system.station.repository.DiningTableRepository;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.entity.StoreMembership;
import com.restaurant.system.user.repository.StoreMembershipRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the minimal Store-local baseline that makes a newly created Store
 * usable without exposing the synthetic Part 2 acceptance workflow to Owner.
 */
@Service
public class OperationalStoreBaselineProvisioner {

    private final DiningTableRepository diningTableRepository;
    private final StoreMembershipRepository storeMembershipRepository;
    private final StoreLogicalPrinterRoleRepository printerRoleRepository;

    public OperationalStoreBaselineProvisioner(
        DiningTableRepository diningTableRepository,
        StoreMembershipRepository storeMembershipRepository,
        StoreLogicalPrinterRoleRepository printerRoleRepository
    ) {
        this.diningTableRepository = diningTableRepository;
        this.storeMembershipRepository = storeMembershipRepository;
        this.printerRoleRepository = printerRoleRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void provision(Store store, AuthenticatedUser owner) {
        requireScope(store, owner);
        LocalDateTime now = LocalDateTime.now();
        ensureOwnerMembership(store, owner, now);
        upsertTable(store, "T01", "Table 1", 1, now);
        upsertTable(store, "T02", "Table 2", 2, now);
        upsertPrinterRole(store, "GRAB", "GRAB", "GRAB", now);
        upsertPrinterRole(store, "FRONTDESK_RECEIPT", "FRONTDESK_RECEIPT", "Frontdesk receipt", now);

        // Optional hardware is deliberately not provisioned and is not a Store
        // lifecycle blocker. Printing management remains available while the
        // runtime transport stays safely disabled and endpoint-free.
        store.printing_mode = "DISABLED";
        store.printing_enabled = false;
        store.updated_at = now;
    }

    private void ensureOwnerMembership(Store store, AuthenticatedUser owner, LocalDateTime now) {
        StoreMembership membership = storeMembershipRepository
            .findFirstByUserIdAndStoreId(owner.userId(), store.id)
            .orElseGet(StoreMembership::new);
        if (membership.id != null && !Objects.equals(membership.organizationId, store.organization_id)) {
            throw new BusinessException("STORE_OWNER_MEMBERSHIP_ORGANIZATION_CONFLICT");
        }
        membership.organizationId = store.organization_id;
        membership.storeId = store.id;
        membership.userId = owner.userId();
        membership.roleId = owner.roleId();
        membership.roleCode = owner.roleCode();
        membership.isActive = true;
        membership.createdAt = membership.createdAt == null ? now : membership.createdAt;
        membership.updatedAt = now;
        storeMembershipRepository.save(membership);
    }

    private void upsertTable(Store store, String code, String name, int sortOrder, LocalDateTime now) {
        List<DiningTable> existing = diningTableRepository.findAllByStoreIdAndTableCode(store.id, code);
        if (existing.size() > 1) {
            throw new BusinessException("STORE_BASELINE_TABLE_DUPLICATE");
        }
        DiningTable table = existing.isEmpty() ? new DiningTable() : existing.get(0);
        table.store_id = store.id;
        table.table_code = code;
        table.table_name = name;
        table.area_name = "Dining";
        table.table_config = "split_supported";
        table.capacity = 2;
        table.supports_split = true;
        table.sort_order = sortOrder;
        table.is_active = true;
        table.created_at = table.created_at == null ? now : table.created_at;
        table.updated_at = now;
        diningTableRepository.save(table);
    }

    private void upsertPrinterRole(
        Store store,
        String roleCode,
        String moduleCode,
        String displayName,
        LocalDateTime now
    ) {
        StoreLogicalPrinterRoleEntity role = printerRoleRepository
            .findByStoreIdAndRoleCode(store.id, roleCode)
            .orElseGet(StoreLogicalPrinterRoleEntity::new);
        StoreLogicalPrinterRoleEntity moduleRole = printerRoleRepository
            .findByStoreIdAndModuleCode(store.id, moduleCode)
            .orElse(null);
        if (moduleRole != null && !Objects.equals(moduleRole.id, role.id)) {
            throw new BusinessException("STORE_BASELINE_PRINTER_MODULE_DUPLICATE");
        }
        role.organization_id = store.organization_id;
        role.store_id = store.id;
        role.role_code = roleCode;
        role.module_code = moduleCode;
        role.display_name = displayName;
        role.mode = "DISABLED";
        role.enabled = true;
        role.required = true;
        role.physical_binding_status = "UNBOUND";
        role.assigned_printer_id = null;
        role.created_at = role.created_at == null ? now : role.created_at;
        role.updated_at = now;
        printerRoleRepository.save(role);
    }

    private void requireScope(Store store, AuthenticatedUser owner) {
        if (store == null || store.id == null || store.organization_id == null
            || owner == null || owner.userId() == null || owner.roleId() == null
            || !"OWNER".equalsIgnoreCase(owner.roleCode())) {
            throw new BusinessException("STORE_BASELINE_SCOPE_INVALID");
        }
    }
}
