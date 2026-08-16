package com.restaurant.system.owner.provisioning;

import com.restaurant.system.menu.combo.StoreComboComponentRepository;
import com.restaurant.system.menu.entity.MenuItem;
import com.restaurant.system.menu.repository.MenuCategoryRepository;
import com.restaurant.system.menu.repository.MenuItemOptionRepository;
import com.restaurant.system.menu.repository.MenuItemRepository;
import com.restaurant.system.menu.pricing.StorePricingPolicyRepository;
import com.restaurant.system.modules.StoreModuleRepository;
import com.restaurant.system.printing.rules.PrintingDisplayRuleSetRepository;
import com.restaurant.system.station.repository.StationRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PhaseBPart1ProvisioningValidator {

    private final StationRepository stationRepository;
    private final MenuCategoryRepository categoryRepository;
    private final MenuItemRepository itemRepository;
    private final MenuItemOptionRepository optionRepository;
    private final StoreModuleRepository moduleRepository;
    private final StorePricingPolicyRepository pricingPolicyRepository;
    private final StoreComboComponentRepository comboComponentRepository;
    private final PrintingDisplayRuleSetRepository printingRuleSetRepository;
    private final StoreMenuMasterMappingRepository mappingRepository;

    public PhaseBPart1ProvisioningValidator(
        StationRepository stationRepository,
        MenuCategoryRepository categoryRepository,
        MenuItemRepository itemRepository,
        MenuItemOptionRepository optionRepository,
        StoreModuleRepository moduleRepository,
        StorePricingPolicyRepository pricingPolicyRepository,
        StoreComboComponentRepository comboComponentRepository,
        PrintingDisplayRuleSetRepository printingRuleSetRepository,
        StoreMenuMasterMappingRepository mappingRepository
    ) {
        this.stationRepository = stationRepository;
        this.categoryRepository = categoryRepository;
        this.itemRepository = itemRepository;
        this.optionRepository = optionRepository;
        this.moduleRepository = moduleRepository;
        this.pricingPolicyRepository = pricingPolicyRepository;
        this.comboComponentRepository = comboComponentRepository;
        this.printingRuleSetRepository = printingRuleSetRepository;
        this.mappingRepository = mappingRepository;
    }

    public PhaseBProvisioningValidationResult validate(
        Long storeId,
        Long masterMenuVersionId,
        int expectedStationCount,
        int expectedModuleCount,
        OwnerStoreProvisioningCounts expectedCounts
    ) {
        List<String> issues = new ArrayList<>();
        assertEquals("STATION_COUNT", expectedStationCount, stationRepository.countAllByStoreId(storeId), issues);
        assertEquals("CATEGORY_COUNT", expectedCounts.categoryCount(), categoryRepository.countAllByStoreId(storeId), issues);
        assertEquals("ITEM_COUNT", expectedCounts.itemCount(), itemRepository.countAllByStoreId(storeId), issues);

        List<Long> itemIds = itemRepository.findAllByStoreIdOrderByIdAsc(storeId).stream()
            .map(item -> item.id)
            .toList();
        int optionCount = itemIds.isEmpty()
            ? 0
            : optionRepository.findAllByStoreIdAndMenuItemIdsOrdered(storeId, itemIds).size();
        assertEquals("OPTION_COUNT", expectedCounts.optionCount(), optionCount, issues);
        assertEquals("MODULE_COUNT", expectedModuleCount, moduleRepository.findAllByStoreIdOrderByIdAsc(storeId).size(), issues);
        if (pricingPolicyRepository.findByStoreId(storeId).isEmpty()) {
            issues.add("PRICING_POLICY_MISSING");
        }
        assertEquals(
            "COMBO_COMPONENT_COUNT",
            expectedCounts.comboComponentCount(),
            comboComponentRepository.findActiveByStoreIdOrdered(storeId).size(),
            issues
        );
        printingRuleSetRepository.findByStoreId(storeId)
            .filter(ruleSet -> ruleSet.active_revision_id != null)
            .orElseGet(() -> {
                issues.add("PRINTING_DISPLAY_RULE_SET_MISSING");
                return null;
            });
        int expectedMappings = expectedCounts.categoryCount() + expectedCounts.itemCount() + expectedCounts.optionCount();
        assertEquals(
            "MASTER_MAPPING_COUNT",
            expectedMappings,
            mappingRepository.findAllByStoreAndMasterVersion(storeId, masterMenuVersionId).size(),
            issues
        );
        return new PhaseBProvisioningValidationResult(issues.isEmpty() ? "PASS" : "BLOCKING", issues);
    }

    private void assertEquals(String code, long expected, long actual, List<String> issues) {
        if (expected != actual) {
            issues.add(code + ": expected=" + expected + ", actual=" + actual);
        }
    }
}
