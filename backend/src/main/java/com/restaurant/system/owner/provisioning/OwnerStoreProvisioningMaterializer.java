package com.restaurant.system.owner.provisioning;

import com.fasterxml.jackson.databind.JsonNode;
import com.restaurant.system.menu.combo.StoreComboComponent;
import com.restaurant.system.menu.combo.StoreComboComponentRepository;
import com.restaurant.system.menu.combo.StoreComboGroup;
import com.restaurant.system.menu.combo.StoreComboGroupRepository;
import com.restaurant.system.menu.entity.MenuCategory;
import com.restaurant.system.menu.entity.MenuItem;
import com.restaurant.system.menu.entity.MenuItemOption;
import com.restaurant.system.menu.pricing.StandardSize;
import com.restaurant.system.menu.pricing.StorePricingPolicy;
import com.restaurant.system.menu.pricing.StorePricingPolicyRepository;
import com.restaurant.system.menu.repository.MenuCategoryRepository;
import com.restaurant.system.menu.repository.MenuItemOptionRepository;
import com.restaurant.system.menu.repository.MenuItemRepository;
import com.restaurant.system.menu.service.MenuRevisionService;
import com.restaurant.system.modules.StoreModule;
import com.restaurant.system.modules.StoreModuleRepository;
import com.restaurant.system.owner.exception.OwnerStoreProvisioningException;
import com.restaurant.system.owner.master.ChainMasterMenuCategoryEntity;
import com.restaurant.system.owner.master.ChainMasterMenuOptionEntity;
import com.restaurant.system.owner.master.ChainMasterMenuProductEntity;
import com.restaurant.system.owner.profile.StoreProfileArtifactEntity;
import com.restaurant.system.owner.profile.StoreProfileCanonicalJson;
import com.restaurant.system.owner.provisioning.part2.StoreActivationRequestCoordinator;
import com.restaurant.system.owner.provisioning.part2.StoreReadinessResponse;
import com.restaurant.system.owner.provisioning.part2.StoreReadinessService;
import com.restaurant.system.platform.entity.Organization;
import com.restaurant.system.platform.repository.OrganizationRepository;
import com.restaurant.system.printing.rules.PrintingDisplayRuleDefaults;
import com.restaurant.system.printing.rules.PrintingDisplayRuleRevision;
import com.restaurant.system.printing.rules.PrintingDisplayRuleRevisionRepository;
import com.restaurant.system.printing.rules.PrintingDisplayRuleSet;
import com.restaurant.system.printing.rules.PrintingDisplayRuleSetRepository;
import com.restaurant.system.station.entity.Station;
import com.restaurant.system.station.repository.StationRepository;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.repository.StoreRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OwnerStoreProvisioningMaterializer {

    private static final String SYNTHETIC_RESULT_CODE = "STORE_CREATED_LIVE";
    private static final String BUSINESS_RESULT_CODE = "BUSINESS_STORE_CREATED_LIVE";

    private final OrganizationRepository organizationRepository;
    private final StoreRepository storeRepository;
    private final StoreModuleRepository moduleRepository;
    private final StationRepository stationRepository;
    private final MenuCategoryRepository categoryRepository;
    private final MenuItemRepository itemRepository;
    private final MenuItemOptionRepository optionRepository;
    private final StorePricingPolicyRepository pricingPolicyRepository;
    private final StoreComboGroupRepository comboGroupRepository;
    private final StoreComboComponentRepository comboComponentRepository;
    private final PrintingDisplayRuleSetRepository printingRuleSetRepository;
    private final PrintingDisplayRuleRevisionRepository printingRuleRevisionRepository;
    private final StoreMenuMasterMappingRepository mappingRepository;
    private final MenuRevisionService menuRevisionService;
    private final PhaseBPart1ProvisioningValidator provisioningValidator;
    private final OwnerStoreProvisioningRequestCoordinator requestCoordinator;
    private final OperationalStoreBaselineProvisioner baselineProvisioner;
    private final StoreReadinessService readinessService;
    private final StoreActivationRequestCoordinator activationRequestCoordinator;

    public OwnerStoreProvisioningMaterializer(
        OrganizationRepository organizationRepository,
        StoreRepository storeRepository,
        StoreModuleRepository moduleRepository,
        StationRepository stationRepository,
        MenuCategoryRepository categoryRepository,
        MenuItemRepository itemRepository,
        MenuItemOptionRepository optionRepository,
        StorePricingPolicyRepository pricingPolicyRepository,
        StoreComboGroupRepository comboGroupRepository,
        StoreComboComponentRepository comboComponentRepository,
        PrintingDisplayRuleSetRepository printingRuleSetRepository,
        PrintingDisplayRuleRevisionRepository printingRuleRevisionRepository,
        StoreMenuMasterMappingRepository mappingRepository,
        MenuRevisionService menuRevisionService,
        PhaseBPart1ProvisioningValidator provisioningValidator,
        OwnerStoreProvisioningRequestCoordinator requestCoordinator,
        OperationalStoreBaselineProvisioner baselineProvisioner,
        StoreReadinessService readinessService,
        StoreActivationRequestCoordinator activationRequestCoordinator
    ) {
        this.organizationRepository = organizationRepository;
        this.storeRepository = storeRepository;
        this.moduleRepository = moduleRepository;
        this.stationRepository = stationRepository;
        this.categoryRepository = categoryRepository;
        this.itemRepository = itemRepository;
        this.optionRepository = optionRepository;
        this.pricingPolicyRepository = pricingPolicyRepository;
        this.comboGroupRepository = comboGroupRepository;
        this.comboComponentRepository = comboComponentRepository;
        this.printingRuleSetRepository = printingRuleSetRepository;
        this.printingRuleRevisionRepository = printingRuleRevisionRepository;
        this.mappingRepository = mappingRepository;
        this.menuRevisionService = menuRevisionService;
        this.provisioningValidator = provisioningValidator;
        this.requestCoordinator = requestCoordinator;
        this.baselineProvisioner = baselineProvisioner;
        this.readinessService = readinessService;
        this.activationRequestCoordinator = activationRequestCoordinator;
    }

    @Transactional
    public OwnerStoreProvisioningResult materialize(
        OwnerStoreProvisioningReservation reservation,
        ResolvedOwnerStoreProvisioningInput input
    ) {
        OwnerStoreProvisioningCommand command = input.command();
        Organization organization = organizationRepository.findByIdForUpdate(command.organizationId())
            .orElseThrow(() -> conflict("ORGANIZATION_NOT_FOUND", "Organization not found"));
        if (!"active".equalsIgnoreCase(organization.status)) {
            throw conflict("ORGANIZATION_INACTIVE", "Organization must be active to create a Store");
        }
        requireUniqueStoreCode(command.organizationId(), command.storeCode());
        Map<String, StoreProfileArtifactEntity> artifacts = indexArtifacts(input.artifacts());
        JsonNode profileContent = StoreProfileCanonicalJson.parse(input.profileVersion().content_json);
        LocalDateTime now = LocalDateTime.now();

        Store store = createStore(command, input, profileContent, now);
        List<JsonNode> moduleNodes = iterable(profileContent.path("module_defaults").path("modules"));
        materializeModules(store.id, command, moduleNodes, now);
        Map<String, Station> stationByRef = materializeStations(store.id, artifacts.get("STATION_TEMPLATE"), now);
        Map<String, MenuCategory> categoryByMasterKey = materializeCategories(store.id, input, now);
        Map<String, MenuItem> itemByMasterKey = materializeProducts(store.id, input, categoryByMasterKey, stationByRef, now);
        materializeOptions(store.id, input, itemByMasterKey, now);
        materializePricingPolicy(store.id, artifacts.get("PRICING_POLICY"), now);
        pricingPolicyRepository.mirrorPolicyToSizeAndComboOptions(store.id);
        materializeComboConfiguration(store.id, artifacts.get("COMBO_CONFIGURATION"), itemByMasterKey, now);
        materializePrintingRules(store.id, artifacts.get("PRINTING_DISPLAY_RULES"), now);

        menuRevisionService.incrementRevision(store.id);
        store.lifecycle_status = "READY_FOR_REVIEW";
        store.updated_at = now;
        storeRepository.save(store);

        OwnerStoreProvisioningCounts counts = new OwnerStoreProvisioningCounts(
            stationByRef.size(),
            input.categories().size(),
            input.products().size(),
            input.options().size(),
            1,
            comboComponentRepository.findActiveByStoreIdOrdered(store.id).size(),
            1
        );
        PhaseBProvisioningValidationResult validation = provisioningValidator.validate(
            store.id,
            input.masterVersion().id,
            stationByRef.size(),
            moduleNodes.size(),
            counts
        );
        if (validation.blocking()) {
            throw conflict("PHASE_B_PROVISIONING_VALIDATION_FAILED", String.join(", ", validation.issues()));
        }

        baselineProvisioner.provision(store, command.actor());
        StoreReadinessResponse readiness = readinessService.evaluateOperationalBaseline(
            command.organizationId(),
            store.id,
            command.actor().userId()
        );
        if (!Boolean.TRUE.equals(readiness.ready)) {
            throw conflict("STORE_OPERATIONAL_BASELINE_NOT_READY", "New Store operational baseline validation failed");
        }
        activationRequestCoordinator.recordAutomaticActivation(
            command.organizationId(),
            store.id,
            reservation.requestId(),
            input.requestFingerprint(),
            readiness.evidence_id,
            readiness.readiness_fingerprint,
            command.actor().userId()
        );
        store.status = "active";
        store.lifecycle_status = "ACTIVE";
        store.updated_at = LocalDateTime.now();
        storeRepository.save(store);

        OwnerStoreProvisioningReservation completed = requestCoordinator.complete(new OwnerStoreProvisioningSuccessEvidence(
            reservation.requestId(),
            command.organizationId(),
            store.id,
            command.profileCode(),
            command.profileVersion(),
            command.profileFingerprintSha256(),
            command.masterMenuKey(),
            command.masterMenuVersion(),
            command.masterMenuFingerprintSha256(),
            validation.status(),
            counts,
            command.isBusinessCreation() ? BUSINESS_RESULT_CODE : SYNTHETIC_RESULT_CODE
        ));
        return toResult(completed);
    }

    private Store createStore(
        OwnerStoreProvisioningCommand command,
        ResolvedOwnerStoreProvisioningInput input,
        JsonNode profileContent,
        LocalDateTime now
    ) {
        JsonNode settings = profileContent.path("source_store_semantics");
        JsonNode operational = profileContent.path("template_references");
        Store store = new Store();
        store.organization_id = command.organizationId();
        store.name = command.storeName().trim();
        store.code = command.storeCode().trim();
        store.status = "inactive";
        store.store_kind = command.isBusinessCreation() ? "BUSINESS" : "VALIDATION_FIXTURE";
        store.lifecycle_status = "CONFIGURING";
        store.provisioning_source = "PHASE_B_OWNER_PROVISIONING";
        store.enable_bar_kitchen_tasks = false;
        store.printing_enabled = false;
        store.printing_mode = "DISABLED";
        store.menu_revision = 1L;
        store.menu_updated_at = now;
        store.provisioned_profile_code = command.profileCode();
        store.provisioned_profile_version = command.profileVersion();
        store.provisioned_profile_fingerprint_sha256 = command.profileFingerprintSha256();
        store.provisioned_master_menu_key = command.masterMenuKey();
        store.provisioned_master_menu_version = command.masterMenuVersion();
        store.provisioned_master_menu_fingerprint_sha256 = command.masterMenuFingerprintSha256();
        store.created_at = now;
        store.updated_at = now;
        return storeRepository.save(store);
    }

    private void materializeModules(
        Long storeId,
        OwnerStoreProvisioningCommand command,
        List<JsonNode> moduleNodes,
        LocalDateTime now
    ) {
        for (JsonNode moduleNode : moduleNodes) {
            StoreModule module = new StoreModule();
            module.store_id = storeId;
            module.module_key = requiredText(moduleNode, "module_key", "MODULE_KEY_REQUIRED");
            module.enabled = moduleNode.path("enabled").asBoolean(false);
            module.source = "PROFILE_DEFAULT";
            module.configuration_status = "CONFIGURED";
            module.profile_code = command.profileCode();
            module.profile_version = command.profileVersion();
            module.metadata_json = "{\"phase_b_source\":\"STORE_PROFILE\",\"master_menu_key\":\""
                + command.masterMenuKey() + "\",\"master_menu_version\":\"" + command.masterMenuVersion() + "\"}";
            module.created_at = now;
            module.updated_at = now;
            moduleRepository.save(module);
        }
    }

    private Map<String, Station> materializeStations(
        Long storeId,
        StoreProfileArtifactEntity stationArtifact,
        LocalDateTime now
    ) {
        JsonNode root = requireArtifactJson(stationArtifact, "STATION_TEMPLATE");
        Map<String, Station> byRef = new LinkedHashMap<>();
        for (JsonNode stationNode : root.path("stations")) {
            Station station = new Station();
            station.store_id = storeId;
            station.code = requiredText(stationNode, "code", "STATION_CODE_REQUIRED");
            station.name = requiredText(stationNode, "name", "STATION_NAME_REQUIRED");
            station.name_zh = station.name;
            station.name_en = station.name;
            station.station_type = stationType(station.code);
            station.sort_order = stationNode.path("sort_order").asInt(0);
            station.is_active = stationNode.path("enabled").asBoolean(true);
            station.created_at = now;
            station.updated_at = now;
            Station saved = stationRepository.save(station);
            byRef.put(requiredText(stationNode, "station_ref", "STATION_REF_REQUIRED"), saved);
        }
        return byRef;
    }

    private Map<String, MenuCategory> materializeCategories(
        Long storeId,
        ResolvedOwnerStoreProvisioningInput input,
        LocalDateTime now
    ) {
        Map<String, MenuCategory> byMasterKey = new LinkedHashMap<>();
        for (ChainMasterMenuCategoryEntity masterCategory : input.categories()) {
            MenuCategory category = new MenuCategory();
            category.store_id = storeId;
            category.code = masterCategory.code;
            category.name_zh = masterCategory.name_zh;
            category.name_en = masterCategory.name_en;
            category.sort_order = masterCategory.sort_order;
            category.is_active = masterCategory.default_active;
            category.created_at = now;
            category.updated_at = now;
            MenuCategory saved = categoryRepository.save(category);
            byMasterKey.put(masterCategory.master_category_key, saved);
            mappingRepository.save(mapping(
                storeId,
                input.masterVersion().id,
                "CATEGORY",
                saved.id,
                masterCategory.master_category_key,
                null,
                null,
                now
            ));
        }
        return byMasterKey;
    }

    private Map<String, MenuItem> materializeProducts(
        Long storeId,
        ResolvedOwnerStoreProvisioningInput input,
        Map<String, MenuCategory> categoryByMasterKey,
        Map<String, Station> stationByRef,
        LocalDateTime now
    ) {
        Map<String, MenuItem> byMasterKey = new LinkedHashMap<>();
        for (ChainMasterMenuProductEntity product : input.products()) {
            MenuCategory category = Optional.ofNullable(categoryByMasterKey.get(product.master_category_key))
                .orElseThrow(() -> conflict("MASTER_CATEGORY_UNRESOLVED", product.master_category_key));
            Station station = product.station_ref == null ? null : stationByRef.get(product.station_ref);
            if (station == null) {
                throw conflict("MASTER_STATION_UNRESOLVED", product.station_ref);
            }
            MenuItem item = new MenuItem();
            item.store_id = storeId;
            item.category_id = category.id;
            item.station_id = station.id;
            item.name_zh = product.name_zh;
            item.name_en = product.name_en;
            item.sku = product.sku;
            item.item_type = product.item_type;
            item.base_price = product.base_price;
            item.cost_per_item = product.cost_per_item;
            item.is_active = product.default_active;
            item.is_sold_out = product.default_sold_out;
            item.sort_order = product.sort_order;
            item.created_at = now;
            item.updated_at = now;
            MenuItem saved = itemRepository.save(item);
            byMasterKey.put(product.master_product_key, saved);
            mappingRepository.save(mapping(
                storeId,
                input.masterVersion().id,
                "ITEM",
                saved.id,
                product.master_category_key,
                product.master_product_key,
                null,
                now
            ));
        }
        return byMasterKey;
    }

    private void materializeOptions(
        Long storeId,
        ResolvedOwnerStoreProvisioningInput input,
        Map<String, MenuItem> itemByMasterKey,
        LocalDateTime now
    ) {
        Map<String, MenuItemOption> optionByMasterKey = new LinkedHashMap<>();
        Map<String, ProvisionedStandardSizePlan.Decision> standardSizePlan =
            ProvisionedStandardSizePlan.from(input.options());
        for (ChainMasterMenuOptionEntity masterOption : input.options()) {
            MenuItem item = Optional.ofNullable(itemByMasterKey.get(masterOption.master_product_key))
                .orElseThrow(() -> conflict("MASTER_PRODUCT_UNRESOLVED", masterOption.master_product_key));
            MenuItemOption option = new MenuItemOption();
            option.menu_item_id = item.id;
            ProvisionedStandardSizePlan.Decision sizeDecision = standardSizePlan.get(masterOption.master_option_key);
            applyOptionIdentity(option, masterOption, sizeDecision);
            option.parent_option_id = null;
            option.sort_order = masterOption.sort_order;
            if (sizeDecision == null) {
                option.name_zh = masterOption.name_zh;
                option.name_en = masterOption.name_en;
            }
            option.price_delta = masterOption.price_delta == null ? BigDecimal.ZERO : masterOption.price_delta;
            if (sizeDecision == null) {
                option.is_active = masterOption.default_active;
            }
            option.created_at = now;
            option.updated_at = now;
            MenuItemOption saved = optionRepository.save(option);
            optionByMasterKey.put(masterOption.master_option_key, saved);
            mappingRepository.save(mapping(
                storeId,
                input.masterVersion().id,
                "OPTION",
                saved.id,
                null,
                masterOption.master_product_key,
                masterOption.master_option_key,
                now
            ));
        }
        for (ChainMasterMenuOptionEntity masterOption : input.options()) {
            if (masterOption.parent_master_option_key == null) {
                continue;
            }
            MenuItemOption option = optionByMasterKey.get(masterOption.master_option_key);
            MenuItemOption parent = optionByMasterKey.get(masterOption.parent_master_option_key);
            if (option == null || parent == null) {
                throw conflict("MASTER_OPTION_PARENT_UNRESOLVED", masterOption.master_option_key);
            }
            option.parent_option_id = parent.id;
            option.updated_at = now;
            optionRepository.save(option);
        }
    }

    static void applyOptionIdentity(
        MenuItemOption option,
        ChainMasterMenuOptionEntity masterOption,
        ProvisionedStandardSizePlan.Decision sizeDecision
    ) {
        if (sizeDecision == null) {
            option.option_type = masterOption.option_type;
            option.option_code = masterOption.code;
            option.option_group = masterOption.option_group;
            return;
        }

        StandardSize size = sizeDecision.size();
        option.option_type = "size";
        option.parent_option_id = null;
        option.is_active = sizeDecision.active();
        if (sizeDecision.canonical()) {
            option.option_group = "SIZE";
            option.option_code = size.code;
            option.name_zh = size.labelZh;
            option.name_en = size.labelEn;
            return;
        }

        // Retain one Store-local row and Master mapping for every legacy Master
        // option, but keep duplicate/inactive legacy Size rows outside the
        // canonical SIZE set with a stable Master-derived identity.
        option.option_group = null;
        option.option_code = ProvisionedStandardSizePlan.stableLegacyCode(masterOption, size);
        option.name_zh = masterOption.name_zh;
        option.name_en = masterOption.name_en;
    }

    private void materializePricingPolicy(Long storeId, StoreProfileArtifactEntity pricingArtifact, LocalDateTime now) {
        JsonNode policy = requireArtifactJson(pricingArtifact, "PRICING_POLICY").path("store_pricing_policy");
        StorePricingPolicy pricing = new StorePricingPolicy();
        pricing.store_id = storeId;
        pricing.size_small_delta = decimal(policy, "size_small_delta");
        pricing.size_regular_delta = decimal(policy, "size_regular_delta");
        pricing.size_large_delta = decimal(policy, "size_large_delta");
        pricing.combo_delta = decimal(policy, "combo_delta");
        pricing.policy_revision = 1L;
        pricing.created_at = now;
        pricing.updated_at = now;
        pricingPolicyRepository.save(pricing);
    }

    private void materializeComboConfiguration(
        Long storeId,
        StoreProfileArtifactEntity comboArtifact,
        Map<String, MenuItem> itemByMasterKey,
        LocalDateTime now
    ) {
        JsonNode root = requireArtifactJson(comboArtifact, "COMBO_CONFIGURATION");
        Map<String, StoreComboGroup> groupByCode = new LinkedHashMap<>();
        for (JsonNode componentNode : root.path("components")) {
            String groupCode = requiredText(componentNode, "component_group", "COMBO_GROUP_REQUIRED");
            groupByCode.computeIfAbsent(groupCode, code -> createComboGroup(storeId, code, now));
        }
        for (JsonNode componentNode : root.path("components")) {
            String groupCode = requiredText(componentNode, "component_group", "COMBO_GROUP_REQUIRED");
            StoreComboGroup group = groupByCode.get(groupCode);
            StoreComboComponent component = new StoreComboComponent();
            component.store_id = storeId;
            component.group_id = group.id;
            component.component_group = groupCode;
            component.component_code = requiredText(componentNode, "component_code", "COMBO_COMPONENT_REQUIRED");
            component.name_zh = requiredText(componentNode, "name_zh", "COMBO_COMPONENT_NAME_REQUIRED");
            component.name_en = requiredText(componentNode, "name_en", "COMBO_COMPONENT_NAME_REQUIRED");
            component.enabled = componentNode.path("enabled").asBoolean(true);
            component.display_order = componentNode.path("display_order").asInt(0);
            component.linked_menu_item_id = linkedComboSideItemId(component.component_code, itemByMasterKey);
            component.business_behavior = component.linked_menu_item_id == null ? "NO_KITCHEN_TASK" : "LEGACY_COMBO_SIDE_TASK";
            component.created_at = now;
            component.updated_at = now;
            comboComponentRepository.save(component);
        }
        for (StoreComboGroup group : groupByCode.values()) {
            List<StoreComboComponent> components = comboComponentRepository.findAllByStoreIdAndGroupIdOrdered(storeId, group.id);
            group.default_component_code = components.stream()
                .filter(component -> Boolean.TRUE.equals(component.enabled))
                .map(component -> component.component_code)
                .findFirst()
                .orElse(null);
            group.updated_at = now;
            comboGroupRepository.save(group);
        }
    }

    private void materializePrintingRules(Long storeId, StoreProfileArtifactEntity printingArtifact, LocalDateTime now) {
        JsonNode content = requireArtifactJson(printingArtifact, "PRINTING_DISPLAY_RULES");
        String canonicalContent = StoreProfileCanonicalJson.canonicalize(content);
        String fingerprint = StoreProfileCanonicalJson.sha256(canonicalContent);
        if (!PrintingDisplayRuleDefaults.DEFAULT_FINGERPRINT.equals(fingerprint)) {
            throw conflict("PRINTING_RULE_FINGERPRINT_MISMATCH", "Printing display rule artifact fingerprint mismatch");
        }
        PrintingDisplayRuleSet ruleSet = new PrintingDisplayRuleSet();
        ruleSet.store_id = storeId;
        ruleSet.status = "ACTIVE";
        ruleSet.created_at = now;
        ruleSet.updated_at = now;
        ruleSet = printingRuleSetRepository.save(ruleSet);

        PrintingDisplayRuleRevision revision = new PrintingDisplayRuleRevision();
        revision.rule_set_id = ruleSet.id;
        revision.revision_number = 1;
        revision.status = "PUBLISHED";
        revision.schema_version = PrintingDisplayRuleDefaults.SCHEMA_VERSION;
        revision.content_json = canonicalContent;
        revision.fingerprint_sha256 = fingerprint;
        revision.source_reference = "PHASE_B_PROFILE_PRINTING_DISPLAY_RULES";
        revision.summary = "Initial Phase B Store-owned printing display rules.";
        revision.created_at = now;
        revision.updated_at = now;
        revision.published_at = now;
        revision = printingRuleRevisionRepository.save(revision);

        ruleSet.active_revision_id = revision.id;
        ruleSet.updated_at = now;
        printingRuleSetRepository.save(ruleSet);
    }

    private StoreComboGroup createComboGroup(Long storeId, String groupCode, LocalDateTime now) {
        StoreComboGroup group = new StoreComboGroup();
        group.store_id = storeId;
        group.group_code = groupCode;
        group.name_zh = "COMBO_EGG".equals(groupCode) ? "蛋类" : "小菜";
        group.name_en = "COMBO_EGG".equals(groupCode) ? "Egg" : "Side";
        group.selection_rule = "EXACTLY_ONE";
        group.required = true;
        group.enabled = true;
        group.display_order = "COMBO_EGG".equals(groupCode) ? 10 : 20;
        group.created_at = now;
        group.updated_at = now;
        return comboGroupRepository.save(group);
    }

    private Long linkedComboSideItemId(String componentCode, Map<String, MenuItem> itemByMasterKey) {
        return switch (componentCode) {
            case "combo_edamame" -> itemId(itemByMasterKey, "edamame");
            case "combo_shredded_potato" -> itemId(itemByMasterKey, "shredded_potato");
            case "combo_cucumber_salad" -> itemId(itemByMasterKey, "cucumber_salad");
            default -> null;
        };
    }

    private Long itemId(Map<String, MenuItem> itemByMasterKey, String masterProductKey) {
        MenuItem item = itemByMasterKey.get(masterProductKey);
        return item == null ? null : item.id;
    }

    private StoreMenuMasterMappingEntity mapping(
        Long storeId,
        Long masterVersionId,
        String entityType,
        Long localEntityId,
        String categoryKey,
        String productKey,
        String optionKey,
        LocalDateTime now
    ) {
        StoreMenuMasterMappingEntity mapping = new StoreMenuMasterMappingEntity();
        mapping.store_id = storeId;
        mapping.master_menu_version_id = masterVersionId;
        mapping.entity_type = entityType;
        mapping.local_entity_id = localEntityId;
        mapping.master_category_key = categoryKey;
        mapping.master_product_key = productKey;
        mapping.master_option_key = optionKey;
        mapping.origin = "MASTER";
        mapping.mapping_status = "ACTIVE";
        mapping.created_at = now;
        mapping.updated_at = now;
        return mapping;
    }

    private void requireUniqueStoreCode(Long organizationId, String storeCode) {
        if (!storeRepository.findAllByOrganizationIdAndCodeIgnoreCase(organizationId, storeCode).isEmpty()) {
            throw conflict("STORE_CODE_ALREADY_EXISTS", "Store code already exists in this Organization");
        }
    }

    private Map<String, StoreProfileArtifactEntity> indexArtifacts(List<StoreProfileArtifactEntity> artifacts) {
        Map<String, StoreProfileArtifactEntity> byCode = new LinkedHashMap<>();
        for (StoreProfileArtifactEntity artifact : artifacts) {
            byCode.put(artifact.artifact_code, artifact);
        }
        return byCode;
    }

    private JsonNode requireArtifactJson(StoreProfileArtifactEntity artifact, String artifactCode) {
        if (artifact == null) {
            throw conflict("PROFILE_ARTIFACT_MISSING", artifactCode);
        }
        return StoreProfileCanonicalJson.parse(artifact.content_json);
    }

    private List<JsonNode> iterable(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<JsonNode> values = new ArrayList<>();
        node.forEach(values::add);
        return values;
    }

    private String requiredText(JsonNode node, String field, String errorCode) {
        String value = node == null ? null : node.path(field).asText(null);
        if (value == null || value.isBlank()) {
            throw conflict(errorCode, field);
        }
        return value.trim();
    }

    private BigDecimal decimal(JsonNode node, String field) {
        return new BigDecimal(requiredText(node, field, "PRICING_POLICY_INVALID"));
    }

    private String stationType(String code) {
        return switch (code) {
            case "BAR" -> "BAR";
            case "COLD" -> "COLD";
            default -> "KITCHEN";
        };
    }

    private OwnerStoreProvisioningResult toResult(OwnerStoreProvisioningReservation reservation) {
        return new OwnerStoreProvisioningResult(
            reservation.requestId(),
            reservation.storeId(),
            reservation.status(),
            reservation.replayed(),
            reservation.validationStatus(),
            reservation.resultCode(),
            reservation.errorCode(),
            reservation.counts()
        );
    }

    private OwnerStoreProvisioningException conflict(String code, String message) {
        return new OwnerStoreProvisioningException(code, HttpStatus.CONFLICT, message);
    }
}
