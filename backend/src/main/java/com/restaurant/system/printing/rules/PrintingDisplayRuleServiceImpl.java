package com.restaurant.system.printing.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.owner.profile.StoreProfileCanonicalJson;
import com.restaurant.system.printing.PrintModuleCode;
import com.restaurant.system.printing.entity.PrintJob;
import com.restaurant.system.printing.repository.PrintJobRepository;
import com.restaurant.system.printing.rules.dto.PrintingDisplayRuleDraftRequest;
import com.restaurant.system.printing.rules.dto.PrintingDisplayRulePreviewRequest;
import com.restaurant.system.printing.rules.dto.PrintingDisplayRulePreviewResponse;
import com.restaurant.system.printing.rules.dto.PrintingDisplayRuleRevisionResponse;
import com.restaurant.system.printing.rules.dto.PrintingDisplayRuleSettingsResponse;
import com.restaurant.system.printing.rules.dto.PrintingDisplayRuleValidationResponse;
import com.restaurant.system.printing.semantic.KitchenModifierTokenResolver;
import com.restaurant.system.user.repository.StoreRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PrintingDisplayRuleServiceImpl implements PrintingDisplayRuleService {

    private static final Set<String> ALLOWED_OUTPUTS = Set.of(
        PrintModuleCode.GRAB,
        PrintModuleCode.FRONTDESK_RECEIPT,
        PrintModuleCode.HOT_KITCHEN
    );
    private static final Set<String> ALLOWED_DICTIONARIES = Set.of(
        "SIZE",
        "NOODLE_TYPE",
        "SPICINESS",
        "MODIFIER_ADD",
        "MODIFIER_REMOVE",
        "SOUP_BASE",
        "COMBO"
    );
    private static final Set<String> ALLOWED_CONDITION_KEYS = Set.of(
        "item_sku",
        "dictionary",
        "semantic_code",
        "output_type",
        "size",
        "noodle_type",
        "spiciness",
        "modifier_present",
        "modifier_absent",
        "combo_state"
    );
    private static final Set<String> ALLOWED_TOP_LEVEL_FIELDS = Set.of(
        "schema_version",
        "outputs",
        "item_aliases",
        "dictionaries",
        "conditional_overrides",
        "formatting"
    );
    private static final Set<String> ALLOWED_ITEM_ALIAS_FIELDS = Set.of(
        "item_sku",
        "outputs"
    );
    private static final Set<String> ALLOWED_DICTIONARY_ENTRY_FIELDS = Set.of(
        "semantic_code",
        "match_codes",
        "match_zh",
        "match_en",
        "outputs"
    );
    private static final Set<String> ALLOWED_DICTIONARY_OUTPUT_KEYS = Set.of(
        PrintModuleCode.GRAB,
        PrintModuleCode.FRONTDESK_RECEIPT,
        PrintModuleCode.HOT_KITCHEN,
        "FRONTDESK_RECEIPT_ZH",
        "FRONTDESK_RECEIPT_EN"
    );
    private static final Set<String> ALLOWED_CONDITIONAL_OVERRIDE_FIELDS = Set.of(
        "condition",
        "omit"
    );
    private static final Set<String> ALLOWED_FORMATTING_FIELDS = Set.of(
        "fried_quantity_symbol",
        "single_noodle_quantity",
        "multi_noodle_quantity",
        "addon_quantity_marker",
        "green_compression",
        "frontdesk_combo_prefix"
    );
    private static final Set<String> PROHIBITED_KEY_EXACT = Set.of(
        "printer_id",
        "printer_endpoint",
        "ip_address",
        "host",
        "port",
        "device_id",
        "credential",
        "credentials",
        "secret",
        "token",
        "password",
        "payment",
        "order_total",
        "tax",
        "route_target",
        "print_job_status",
        "dispatch_target",
        "store_id",
        "source_store_id"
    );
    private static final Set<String> PROHIBITED_KEY_FRAGMENTS = Set.of(
        "regex",
        "script",
        "template",
        "expression",
        "function",
        "eval",
        "endpoint",
        "credential",
        "secret",
        "token",
        "password",
        "payment",
        "printer",
        "device",
        "route",
        "socket",
        "url",
        "uri"
    );

    private final PrintingDisplayRuleSetRepository ruleSetRepository;
    private final PrintingDisplayRuleRevisionRepository revisionRepository;
    private final StoreRepository storeRepository;
    private final PrintJobRepository printJobRepository;

    public PrintingDisplayRuleServiceImpl(
        PrintingDisplayRuleSetRepository ruleSetRepository,
        PrintingDisplayRuleRevisionRepository revisionRepository,
        StoreRepository storeRepository,
        PrintJobRepository printJobRepository
    ) {
        this.ruleSetRepository = ruleSetRepository;
        this.revisionRepository = revisionRepository;
        this.storeRepository = storeRepository;
        this.printJobRepository = printJobRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public PrintingDisplayRuleSettingsResponse getSettings(Long storeId) {
        PrintingDisplayRuleSet ruleSet = requireRuleSet(storeId);
        List<PrintingDisplayRuleRevision> revisions = revisionRepository.findAllByRuleSetIdOrderByRevisionNumberDesc(ruleSet.id);
        PrintingDisplayRuleSettingsResponse response = new PrintingDisplayRuleSettingsResponse();
        response.store_id = storeId;
        response.rule_set_id = ruleSet.id;
        response.active_revision_id = ruleSet.active_revision_id;
        response.revisions = revisions.stream().map(this::toResponse).toList();
        response.active_revision = revisions.stream()
            .filter(revision -> revision.id.equals(ruleSet.active_revision_id))
            .findFirst()
            .map(this::toResponse)
            .orElse(null);
        response.draft_revision = revisions.stream()
            .filter(revision -> "DRAFT".equals(revision.status))
            .findFirst()
            .map(this::toResponse)
            .orElse(null);
        return response;
    }

    @Override
    @Transactional
    public PrintingDisplayRuleRevisionResponse saveDraft(PrintingDisplayRuleDraftRequest request) {
        if (request == null || request.store_id == null) {
            throw new BusinessException("store_id is required");
        }
        JsonNode content = request.content == null
            ? StoreProfileCanonicalJson.parse(PrintingDisplayRuleDefaults.DEFAULT_CONTENT_JSON)
            : request.content;
        PrintingDisplayRuleValidationResult validation = validateContent(content);
        if (!validation.valid()) {
            throw new BusinessException("PRINTING_RULE_VALIDATION_FAILED: " + validation.issues());
        }
        PrintingDisplayRuleSet ruleSet = requireRuleSetForUpdate(request.store_id);
        LocalDateTime now = LocalDateTime.now();
        String canonicalContent = StoreProfileCanonicalJson.canonicalize(content);
        String fingerprint = StoreProfileCanonicalJson.sha256(canonicalContent);
        PrintingDisplayRuleRevision draft = revisionRepository.findDraftsByRuleSetId(ruleSet.id).stream().findFirst().orElse(null);
        if (draft == null) {
            draft = new PrintingDisplayRuleRevision();
            draft.rule_set_id = ruleSet.id;
            draft.revision_number = revisionRepository.findMaxRevisionNumber(ruleSet.id) + 1;
            draft.status = "DRAFT";
            draft.schema_version = PrintingDisplayRuleDefaults.SCHEMA_VERSION;
            draft.source_reference = "PHASE_A11_OWNER_DRAFT";
            draft.created_at = now;
        }
        draft.content_json = canonicalContent;
        draft.fingerprint_sha256 = fingerprint;
        draft.summary = truncate(request.summary, 500);
        draft.updated_at = now;
        return toResponse(revisionRepository.save(draft));
    }

    @Override
    @Transactional
    public PrintingDisplayRuleRevisionResponse publishDraft(Long storeId, Long revisionId) {
        if (storeId == null || revisionId == null) {
            throw new BusinessException("store_id and revision_id are required");
        }
        PrintingDisplayRuleSet ruleSet = requireRuleSetForUpdate(storeId);
        PrintingDisplayRuleRevision revision = revisionRepository.findById(revisionId)
            .orElseThrow(() -> new BusinessException("Printing display rule revision not found"));
        if (!ruleSet.id.equals(revision.rule_set_id)) {
            throw new BusinessException("Printing display rule revision does not belong to store");
        }
        if (!"DRAFT".equals(revision.status)) {
            throw new BusinessException("Only DRAFT printing display rule revisions can be published");
        }
        PrintingDisplayRuleValidationResult validation = validateContent(StoreProfileCanonicalJson.parse(revision.content_json));
        if (!validation.valid()) {
            throw new BusinessException("PRINTING_RULE_VALIDATION_FAILED: " + validation.issues());
        }
        LocalDateTime now = LocalDateTime.now();
        revision.status = "PUBLISHED";
        revision.published_at = now;
        revision.updated_at = now;
        revision = revisionRepository.save(revision);
        ruleSet.active_revision_id = revision.id;
        ruleSet.updated_at = now;
        ruleSetRepository.save(ruleSet);
        return toResponse(revision);
    }

    @Override
    @Transactional(readOnly = true)
    public PrintingDisplayRuleValidationResponse validate(Long storeId, JsonNode content) {
        requireStore(storeId);
        PrintingDisplayRuleValidationResult validation = validateContent(content);
        PrintingDisplayRuleValidationResponse response = new PrintingDisplayRuleValidationResponse();
        response.valid = validation.valid();
        response.fingerprint_sha256 = validation.fingerprintSha256();
        response.issues = validation.issues();
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public PrintingDisplayRulePreviewResponse preview(PrintingDisplayRulePreviewRequest request) {
        if (request == null || request.store_id == null) {
            throw new BusinessException("store_id is required");
        }
        requireStore(request.store_id);
        JsonNode content = request.content == null
            ? activeContext(request.store_id).content()
            : request.content;
        PrintingDisplayRuleValidationResult validation = validateContent(content);
        if (!validation.valid()) {
            throw new BusinessException("PRINTING_RULE_VALIDATION_FAILED: " + validation.issues());
        }
        PrintingDisplayRuleContext context = new PrintingDisplayRuleContext(null, null, validation.fingerprintSha256(), content);
        String itemSku = blankToDefault(request.item_sku, "cold_noodle_shredded_chicken");
        String itemNameZh = blankToDefault(request.item_name_zh, "鸡丝凉面");
        String itemNameEn = blankToDefault(request.item_name_en, "Cold Noodle with Shredded Chicken");
        String size = blankToDefault(request.size_zh, "中碗");
        String noodle = blankToDefault(request.noodle_type_zh, "韭叶");
        String spicy = blankToDefault(request.spiciness_zh, "少辣");
        List<String> add = request.modifier_add_codes == null ? List.of("bok_choy") : request.modifier_add_codes;
        List<String> remove = request.modifier_remove_codes == null ? List.of("green_onion") : request.modifier_remove_codes;

        String grabAlias = context.resolveItemAlias(PrintModuleCode.GRAB, itemSku, itemNameZh);
        String hotAlias = context.resolveItemAlias(PrintModuleCode.HOT_KITCHEN, itemSku, grabAlias);
        String frontdeskAlias = context.resolveItemAlias(PrintModuleCode.FRONTDESK_RECEIPT, itemSku, itemNameZh);
        String sizeGrab = context.resolveDictionaryOutput("SIZE", PrintModuleCode.GRAB, null, size, null, "");
        String noodleGrab = resolvePreviewDictionary(context, itemSku, "NOODLE_TYPE", PrintModuleCode.GRAB, noodle);
        String spicyGrab = context.resolveDictionaryOutput("SPICINESS", PrintModuleCode.GRAB, null, spicy, null, "");
        String sizeHot = context.resolveDictionaryOutput("SIZE", PrintModuleCode.HOT_KITCHEN, null, size, null, "");
        String noodleHot = resolvePreviewDictionary(context, itemSku, "NOODLE_TYPE", PrintModuleCode.HOT_KITCHEN, noodle);
        String spicyHot = context.resolveDictionaryOutput("SPICINESS", PrintModuleCode.HOT_KITCHEN, null, spicy, null, "");
        String modifiers = buildPreviewModifiers(context, add, remove);

        PrintingDisplayRulePreviewResponse response = new PrintingDisplayRulePreviewResponse();
        response.grab_preview = sizeGrab + grabAlias + noodleGrab + spicyGrab + "×1" + (modifiers.isBlank() ? "" : " | " + modifiers);
        response.hot_kitchen_preview = sizeHot + hotAlias + noodleHot + spicyHot + " ×1" + (modifiers.isBlank() ? "" : "\n" + modifiers);
        response.frontdesk_receipt_preview = context.resolveDictionaryOutputKey("SIZE", "FRONTDESK_RECEIPT_ZH", null, size, null, "")
            + frontdeskAlias
            + "\n"
            + "辣度: " + context.resolveDictionaryOutput("SPICINESS", PrintModuleCode.FRONTDESK_RECEIPT, null, spicy, null, spicy)
            + (Boolean.TRUE.equals(request.combo) ? "\n小菜: 毛豆" : "");
        response.fingerprint_sha256 = validation.fingerprintSha256();
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public PrintingDisplayRuleContext activeContext(Long storeId) {
        PrintingDisplayRuleSet ruleSet = ruleSetRepository.findByStoreId(storeId).orElse(null);
        if (ruleSet == null || ruleSet.active_revision_id == null) {
            return PrintingDisplayRuleContext.defaultContext();
        }
        PrintingDisplayRuleRevision revision = revisionRepository.findById(ruleSet.active_revision_id).orElse(null);
        if (revision != null && !ruleSet.id.equals(revision.rule_set_id)) {
            return PrintingDisplayRuleContext.defaultContext();
        }
        return PrintingDisplayRuleContext.fromRevision(revision);
    }

    @Override
    @Transactional(readOnly = true)
    public PrintingDisplayRuleContext contextForJob(PrintJob job) {
        if (job != null && job.printingRuleRevisionId != null) {
            PrintingDisplayRuleRevision revision = revisionRepository.findById(job.printingRuleRevisionId).orElse(null);
            PrintingDisplayRuleSet ruleSet = job.store_id == null ? null : ruleSetRepository.findByStoreId(job.store_id).orElse(null);
            if (revision != null && ruleSet != null && ruleSet.id.equals(revision.rule_set_id)) {
                return PrintingDisplayRuleContext.fromRevision(revision);
            }
        }
        return job == null || job.store_id == null ? PrintingDisplayRuleContext.defaultContext() : activeContext(job.store_id);
    }

    @Override
    @Transactional(readOnly = true)
    public PrintingDisplayRuleContext historicalContextForOrder(Long storeId, Long orderId, String moduleCode) {
        if (storeId == null || orderId == null) {
            return activeContext(storeId);
        }
        return printJobRepository.findAllByStoreIdAndOrderId(storeId, orderId).stream()
            .filter(job -> moduleCode == null || moduleCode.equals(job.module_code))
            .filter(job -> job.printingRuleRevisionId != null)
            .findFirst()
            .map(this::contextForJob)
            .orElseGet(() -> activeContext(storeId));
    }

    private PrintingDisplayRuleSet requireRuleSet(Long storeId) {
        requireStore(storeId);
        return ruleSetRepository.findByStoreId(storeId)
            .orElseThrow(() -> new BusinessException("Printing display rules are not materialized for store: " + storeId));
    }

    private PrintingDisplayRuleSet requireRuleSetForUpdate(Long storeId) {
        requireStore(storeId);
        return ruleSetRepository.findByStoreIdForUpdate(storeId).orElseGet(() -> createDefaultRuleSet(storeId));
    }

    private PrintingDisplayRuleSet createDefaultRuleSet(Long storeId) {
        LocalDateTime now = LocalDateTime.now();
        PrintingDisplayRuleSet ruleSet = new PrintingDisplayRuleSet();
        ruleSet.store_id = storeId;
        ruleSet.status = "ACTIVE";
        ruleSet.created_at = now;
        ruleSet.updated_at = now;
        ruleSet = ruleSetRepository.save(ruleSet);

        PrintingDisplayRuleRevision revision = new PrintingDisplayRuleRevision();
        revision.rule_set_id = ruleSet.id;
        revision.revision_number = 1;
        revision.status = "PUBLISHED";
        revision.schema_version = PrintingDisplayRuleDefaults.SCHEMA_VERSION;
        revision.content_json = StoreProfileCanonicalJson.canonicalize(PrintingDisplayRuleDefaults.DEFAULT_CONTENT_JSON);
        revision.fingerprint_sha256 = PrintingDisplayRuleDefaults.DEFAULT_FINGERPRINT;
        revision.source_reference = "PHASE_A11_CURRENT_CODE_EQUIVALENT_DEFAULTS";
        revision.summary = "Initial A11 default revision equivalent to current accepted printing behavior.";
        revision.created_at = now;
        revision.updated_at = now;
        revision.published_at = now;
        revision = revisionRepository.save(revision);
        ruleSet.active_revision_id = revision.id;
        ruleSet.updated_at = now;
        return ruleSetRepository.save(ruleSet);
    }

    private void requireStore(Long storeId) {
        if (storeId == null || !storeRepository.existsById(storeId)) {
            throw new BusinessException("Store not found: " + storeId);
        }
    }

    private PrintingDisplayRuleValidationResult validateContent(JsonNode content) {
        List<PrintingDisplayRuleValidationIssue> issues = new ArrayList<>();
        if (content == null || !content.isObject()) {
            issues.add(issue("CONTENT_REQUIRED", "$", "Printing display rule content must be a JSON object"));
            return new PrintingDisplayRuleValidationResult(false, null, issues);
        }
        validateSchema(content, issues);
        validateTopLevelFields(content, issues);
        scanProhibited(content, "$", issues);
        validateOutputs(content, issues);
        validateItemAliases(content, issues);
        validateDictionaries(content, issues);
        validateConditions(content, issues);
        String fingerprint = issues.isEmpty() ? StoreProfileCanonicalJson.sha256(StoreProfileCanonicalJson.canonicalize(content)) : null;
        return new PrintingDisplayRuleValidationResult(issues.isEmpty(), fingerprint, issues);
    }

    private void validateSchema(JsonNode content, List<PrintingDisplayRuleValidationIssue> issues) {
        if (!PrintingDisplayRuleDefaults.SCHEMA_VERSION.equals(content.path("schema_version").asText(null))) {
            issues.add(issue("INVALID_SCHEMA_VERSION", "$.schema_version", "schema_version must be PRINTING_DISPLAY_RULES_V1"));
        }
    }

    private void validateTopLevelFields(JsonNode content, List<PrintingDisplayRuleValidationIssue> issues) {
        Iterator<String> names = content.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!ALLOWED_TOP_LEVEL_FIELDS.contains(name)) {
                issues.add(issue("UNKNOWN_FIELD", "$." + name, "Unknown printing display rule field"));
            }
        }

        JsonNode formatting = content.path("formatting");
        if (!formatting.isMissingNode() && !formatting.isObject()) {
            issues.add(issue("FORMATTING_INVALID", "$.formatting", "formatting must be an object when present"));
            return;
        }
        if (formatting.isObject()) {
            Iterator<String> formattingFields = formatting.fieldNames();
            while (formattingFields.hasNext()) {
                String field = formattingFields.next();
                if (!ALLOWED_FORMATTING_FIELDS.contains(field)) {
                    issues.add(issue("UNKNOWN_FIELD", "$.formatting." + field, "Unknown formatting field"));
                }
                JsonNode value = formatting.path(field);
                if (!value.isTextual()) {
                    issues.add(issue("FORMATTING_VALUE_INVALID", "$.formatting." + field, "formatting values must be text"));
                }
            }
        }
    }

    private void validateOutputs(JsonNode content, List<PrintingDisplayRuleValidationIssue> issues) {
        JsonNode outputs = content.path("outputs");
        if (!outputs.isArray()) {
            issues.add(issue("OUTPUTS_REQUIRED", "$.outputs", "outputs must be an array"));
            return;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (JsonNode output : outputs) {
            String value = output.asText(null);
            if (!ALLOWED_OUTPUTS.contains(value)) {
                issues.add(issue("UNKNOWN_OUTPUT_TYPE", "$.outputs", "Unknown output type: " + output.asText(null)));
                continue;
            }
            if (!seen.add(value)) {
                issues.add(issue("DUPLICATE_OUTPUT_TYPE", "$.outputs", "Output type must be unique: " + value));
            }
        }
    }

    private void validateItemAliases(JsonNode content, List<PrintingDisplayRuleValidationIssue> issues) {
        Set<String> seen = new LinkedHashSet<>();
        JsonNode aliases = content.path("item_aliases");
        if (!aliases.isArray()) {
            issues.add(issue("ITEM_ALIASES_REQUIRED", "$.item_aliases", "item_aliases must be an array"));
            return;
        }
        for (JsonNode alias : aliases) {
            if (!alias.isObject()) {
                issues.add(issue("ITEM_ALIAS_INVALID", "$.item_aliases[]", "item_aliases entries must be objects"));
                continue;
            }
            Iterator<String> aliasFields = alias.fieldNames();
            while (aliasFields.hasNext()) {
                String field = aliasFields.next();
                if (!ALLOWED_ITEM_ALIAS_FIELDS.contains(field)) {
                    issues.add(issue("UNKNOWN_FIELD", "$.item_aliases[]." + field, "Unknown item alias field"));
                }
            }
            String itemSku = alias.path("item_sku").asText(null);
            if (itemSku == null || itemSku.isBlank()) {
                issues.add(issue("ITEM_SKU_REQUIRED", "$.item_aliases[].item_sku", "item_sku is required"));
                continue;
            }
            JsonNode outputs = alias.path("outputs");
            if (!outputs.isObject()) {
                issues.add(issue("ITEM_ALIAS_OUTPUTS_REQUIRED", "$.item_aliases[].outputs", "outputs object is required"));
                continue;
            }
            Iterator<String> fieldNames = outputs.fieldNames();
            while (fieldNames.hasNext()) {
                String output = fieldNames.next();
                if (!ALLOWED_OUTPUTS.contains(output)) {
                    issues.add(issue("UNKNOWN_OUTPUT_TYPE", "$.item_aliases[].outputs", "Unknown item alias output: " + output));
                    continue;
                }
                String value = outputs.path(output).asText(null);
                if (value != null && value.isBlank()) {
                    issues.add(issue("BLANK_ALIAS", "$.item_aliases[].outputs." + output, "Alias cannot be blank; omit the output to inherit menu name"));
                }
                String identity = stable(itemSku) + "|" + stable(output);
                if (!seen.add(identity)) {
                    issues.add(issue("DUPLICATE_ITEM_ALIAS", "$.item_aliases", "Duplicate item/output alias: " + identity));
                }
            }
        }
    }

    private void validateDictionaries(JsonNode content, List<PrintingDisplayRuleValidationIssue> issues) {
        JsonNode dictionaries = content.path("dictionaries");
        if (!dictionaries.isObject()) {
            issues.add(issue("DICTIONARIES_REQUIRED", "$.dictionaries", "dictionaries object is required"));
            return;
        }
        Iterator<String> names = dictionaries.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!ALLOWED_DICTIONARIES.contains(name)) {
                issues.add(issue("UNKNOWN_DICTIONARY", "$.dictionaries." + name, "Unknown dictionary: " + name));
                continue;
            }
            JsonNode dictionary = dictionaries.path(name);
            if (!dictionary.isArray()) {
                issues.add(issue("DICTIONARY_INVALID", "$.dictionaries." + name, "Dictionary entries must be arrays"));
                continue;
            }
            int index = 0;
            for (JsonNode entry : dictionary) {
                String entryPath = "$.dictionaries." + name + "[" + index + "]";
                if ("MODIFIER_ADD".equals(name) || "MODIFIER_REMOVE".equals(name)) {
                    validateModifierDictionaryEntry(entry, entryPath, issues);
                } else {
                    validateStructuredDictionaryEntry(entry, entryPath, issues);
                }
                index += 1;
            }
        }
    }

    private void validateModifierDictionaryEntry(
        JsonNode entry,
        String path,
        List<PrintingDisplayRuleValidationIssue> issues
    ) {
        if (!entry.isArray() || entry.size() != 2) {
            issues.add(issue("MODIFIER_DICTIONARY_ENTRY_INVALID", path, "Modifier dictionaries must contain [semantic_code, output] pairs only"));
            return;
        }
        for (int i = 0; i < entry.size(); i++) {
            JsonNode value = entry.get(i);
            if (!value.isTextual() || value.asText("").isBlank()) {
                issues.add(issue("MODIFIER_DICTIONARY_ENTRY_INVALID", path + "[" + i + "]", "Modifier dictionary values must be non-blank text"));
            }
        }
    }

    private void validateStructuredDictionaryEntry(
        JsonNode entry,
        String path,
        List<PrintingDisplayRuleValidationIssue> issues
    ) {
        if (!entry.isObject()) {
            issues.add(issue("DICTIONARY_ENTRY_INVALID", path, "Dictionary entries must be structured objects"));
            return;
        }
        Iterator<String> fields = entry.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();
            if (!ALLOWED_DICTIONARY_ENTRY_FIELDS.contains(field)) {
                issues.add(issue("UNKNOWN_FIELD", path + "." + field, "Unknown dictionary entry field"));
            }
        }
        if (entry.path("semantic_code").asText("").isBlank()) {
            issues.add(issue("DICTIONARY_SEMANTIC_CODE_REQUIRED", path + ".semantic_code", "semantic_code is required"));
        }
        validateOptionalTextArray(entry, "match_codes", path, issues);
        validateOptionalTextArray(entry, "match_zh", path, issues);
        validateOptionalTextArray(entry, "match_en", path, issues);
        JsonNode outputs = entry.path("outputs");
        if (!outputs.isObject()) {
            issues.add(issue("DICTIONARY_OUTPUTS_REQUIRED", path + ".outputs", "Dictionary outputs must be an object"));
            return;
        }
        Iterator<String> outputFields = outputs.fieldNames();
        while (outputFields.hasNext()) {
            String output = outputFields.next();
            if (!ALLOWED_DICTIONARY_OUTPUT_KEYS.contains(output)) {
                issues.add(issue("UNKNOWN_OUTPUT_TYPE", path + ".outputs." + output, "Unknown dictionary output: " + output));
            }
            JsonNode value = outputs.path(output);
            if (!value.isTextual()) {
                issues.add(issue("DICTIONARY_OUTPUT_INVALID", path + ".outputs." + output, "Dictionary output values must be text"));
            }
        }
    }

    private void validateOptionalTextArray(
        JsonNode entry,
        String field,
        String path,
        List<PrintingDisplayRuleValidationIssue> issues
    ) {
        JsonNode value = entry.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return;
        }
        if (!value.isArray()) {
            issues.add(issue("DICTIONARY_MATCH_FIELD_INVALID", path + "." + field, field + " must be an array"));
            return;
        }
        int index = 0;
        for (JsonNode element : value) {
            if (!element.isTextual() || element.asText("").isBlank()) {
                issues.add(issue("DICTIONARY_MATCH_FIELD_INVALID", path + "." + field + "[" + index + "]", field + " values must be non-blank text"));
            }
            index += 1;
        }
    }

    private void validateConditions(JsonNode content, List<PrintingDisplayRuleValidationIssue> issues) {
        JsonNode overrides = content.path("conditional_overrides");
        if (overrides.isMissingNode() || overrides.isNull()) {
            return;
        }
        if (!overrides.isArray()) {
            issues.add(issue("CONDITIONAL_OVERRIDES_INVALID", "$.conditional_overrides", "conditional_overrides must be an array"));
            return;
        }
        Set<String> identities = new LinkedHashSet<>();
        for (JsonNode override : overrides) {
            if (!override.isObject()) {
                issues.add(issue("CONDITIONAL_OVERRIDE_INVALID", "$.conditional_overrides[]", "conditional_overrides entries must be objects"));
                continue;
            }
            Iterator<String> overrideFields = override.fieldNames();
            while (overrideFields.hasNext()) {
                String field = overrideFields.next();
                if (!ALLOWED_CONDITIONAL_OVERRIDE_FIELDS.contains(field)) {
                    issues.add(issue("UNKNOWN_FIELD", "$.conditional_overrides[]." + field, "Unknown conditional override field"));
                }
            }
            if (!override.path("omit").isBoolean()) {
                issues.add(issue("CONDITIONAL_OMIT_INVALID", "$.conditional_overrides[].omit", "omit must be boolean"));
            }
            JsonNode condition = override.path("condition");
            if (!condition.isObject()) {
                issues.add(issue("CONDITION_REQUIRED", "$.conditional_overrides[].condition", "condition object is required"));
                continue;
            }
            Iterator<String> keys = condition.fieldNames();
            while (keys.hasNext()) {
                String key = keys.next();
                if (!ALLOWED_CONDITION_KEYS.contains(key)) {
                    issues.add(issue("INVALID_CONDITION_KEY", "$.conditional_overrides[].condition." + key, "Unsupported condition key"));
                }
            }
            String dictionary = stable(condition.path("dictionary").asText(null));
            if (dictionary != null && !ALLOWED_DICTIONARIES.contains(dictionary)) {
                issues.add(issue("UNKNOWN_DICTIONARY", "$.conditional_overrides[].condition.dictionary", "Unknown dictionary: " + dictionary));
            }
            String identity = condition.toString();
            if (!identities.add(identity)) {
                issues.add(issue("DUPLICATE_CONDITION", "$.conditional_overrides", "Duplicate same-precedence condition"));
            }
        }
    }

    private void scanProhibited(JsonNode node, String path, List<PrintingDisplayRuleValidationIssue> issues) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            Iterator<String> fields = node.fieldNames();
            while (fields.hasNext()) {
                String field = fields.next();
                String normalized = field.trim().toLowerCase(Locale.ROOT);
                if (isProhibitedKey(normalized)) {
                    issues.add(issue("PROHIBITED_KEY", path + "." + field, "Printing display rules cannot configure operational/security fields"));
                }
                scanProhibited(node.path(field), path + "." + field, issues);
            }
            return;
        }
        if (node.isArray()) {
            int index = 0;
            for (JsonNode child : node) {
                scanProhibited(child, path + "[" + index + "]", issues);
                index += 1;
            }
            return;
        }
        if (node.isTextual()) {
            String value = node.asText("").toLowerCase(Locale.ROOT);
            if (value.contains("<script")
                || value.contains("javascript:")
                || value.contains("regex:")
                || value.contains("function(")
                || value.contains("=>")
                || value.contains("${")
                || value.contains("{{")) {
                issues.add(issue("EXECUTABLE_CONTENT_REJECTED", path, "Scripts, regex markers and executable expressions are not allowed"));
            }
        }
    }

    private boolean isProhibitedKey(String normalized) {
        if (PROHIBITED_KEY_EXACT.contains(normalized)) {
            return true;
        }
        for (String fragment : PROHIBITED_KEY_FRAGMENTS) {
            if (normalized.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    private PrintingDisplayRuleRevisionResponse toResponse(PrintingDisplayRuleRevision revision) {
        PrintingDisplayRuleRevisionResponse response = new PrintingDisplayRuleRevisionResponse();
        response.id = revision.id;
        response.revision_number = revision.revision_number;
        response.status = revision.status;
        response.schema_version = revision.schema_version;
        response.fingerprint_sha256 = revision.fingerprint_sha256;
        response.source_reference = revision.source_reference;
        response.summary = revision.summary;
        response.content = StoreProfileCanonicalJson.parse(revision.content_json);
        response.created_at = revision.created_at;
        response.updated_at = revision.updated_at;
        response.published_at = revision.published_at;
        return response;
    }

    private String resolvePreviewDictionary(
        PrintingDisplayRuleContext context,
        String itemSku,
        String dictionary,
        String outputType,
        String zh
    ) {
        String semanticCode = context.resolveSemanticCode(dictionary, null, zh, null);
        if (context.shouldOmit(itemSku, dictionary, semanticCode)) {
            return "";
        }
        return context.resolveDictionaryOutput(dictionary, outputType, null, zh, null, zh);
    }

    private String buildPreviewModifiers(PrintingDisplayRuleContext context, List<String> addCodes, List<String> removeCodes) {
        List<String> tokens = new ArrayList<>();
        for (String code : addCodes == null ? List.<String>of() : addCodes) {
            String token = KitchenModifierTokenResolver.resolveAddon("ADD_ON", code, code, null, 1, context);
            if (token != null && !token.isBlank()) {
                tokens.add(token);
            }
        }
        for (String code : removeCodes == null ? List.<String>of() : removeCodes) {
            String token = KitchenModifierTokenResolver.resolveRemove(code, code, null, context);
            if (token != null && !token.isBlank()) {
                tokens.add(token);
            }
        }
        return String.join(" ", tokens);
    }

    private PrintingDisplayRuleValidationIssue issue(String code, String path, String message) {
        return new PrintingDisplayRuleValidationIssue(code, path, message);
    }

    private String stable(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
