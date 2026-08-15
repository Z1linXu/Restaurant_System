package com.restaurant.system.printing.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.restaurant.system.owner.profile.StoreProfileCanonicalJson;
import com.restaurant.system.printing.repository.PrintJobRepository;
import com.restaurant.system.printing.rules.dto.PrintingDisplayRulePreviewRequest;
import com.restaurant.system.printing.rules.dto.PrintingDisplayRuleValidationResponse;
import com.restaurant.system.user.repository.StoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PrintingDisplayRuleServiceValidationTest {

    @Mock
    private PrintingDisplayRuleSetRepository ruleSetRepository;
    @Mock
    private PrintingDisplayRuleRevisionRepository revisionRepository;
    @Mock
    private StoreRepository storeRepository;
    @Mock
    private PrintJobRepository printJobRepository;

    private PrintingDisplayRuleServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PrintingDisplayRuleServiceImpl(
            ruleSetRepository,
            revisionRepository,
            storeRepository,
            printJobRepository
        );
        when(storeRepository.existsById(1L)).thenReturn(true);
    }

    @Test
    void validatesDefaultContractAndReturnsDeterministicFingerprint() {
        PrintingDisplayRuleValidationResponse response = service.validate(
            1L,
            StoreProfileCanonicalJson.parse(PrintingDisplayRuleDefaults.DEFAULT_CONTENT_JSON)
        );

        assertThat(response.valid).isTrue();
        assertThat(response.fingerprint_sha256).isEqualTo(PrintingDisplayRuleDefaults.DEFAULT_FINGERPRINT);
        assertThat(response.issues).isEmpty();
    }

    @Test
    void rejectsOperationalSecretsAndExecutableRuleContent() {
        var content = StoreProfileCanonicalJson.parse("""
            {
              "schema_version": "PRINTING_DISPLAY_RULES_V1",
              "outputs": ["GRAB", "FRONTDESK_RECEIPT", "HOT_KITCHEN"],
              "item_aliases": [
                {"item_sku": "x", "outputs": {"GRAB": "<script>alert(1)</script>"}}
              ],
              "dictionaries": {
                "SIZE": [],
                "MODIFIER_ADD": [],
                "MODIFIER_REMOVE": []
              },
              "printer_id": 10,
              "conditional_overrides": []
            }
            """);

        PrintingDisplayRuleValidationResponse response = service.validate(1L, content);

        assertThat(response.valid).isFalse();
        assertThat(response.issues).extracting(PrintingDisplayRuleValidationIssue::code)
            .contains("PROHIBITED_KEY", "EXECUTABLE_CONTENT_REJECTED");
    }

    @Test
    void rejectsUnknownStructuredFieldsAndNonCanonicalOutputKeys() {
        var content = StoreProfileCanonicalJson.parse("""
            {
              "schema_version": "PRINTING_DISPLAY_RULES_V1",
              "outputs": ["grab", "FRONTDESK_RECEIPT", "HOT_KITCHEN"],
              "raw_template": "{{ printer.ip }}",
              "item_aliases": [
                {"item_sku": "x", "output_aliases": {"GRAB": "x"}, "outputs": {"grab": "x"}}
              ],
              "dictionaries": {
                "size": [],
                "SIZE": [
                  {
                    "semantic_code": "LARGE",
                    "match_regex": ".*large.*",
                    "outputs": {"grab": "大"}
                  }
                ],
                "MODIFIER_ADD": [
                  {"semantic_code": "tea_egg", "output": "+蛋"}
                ]
              },
              "conditional_overrides": [
                {"condition": {"dictionary": "SIZE", "semantic_code": "LARGE", "raw_expression": "return true"}, "omit": true}
              ]
            }
            """);

        PrintingDisplayRuleValidationResponse response = service.validate(1L, content);

        assertThat(response.valid).isFalse();
        assertThat(response.issues).extracting(PrintingDisplayRuleValidationIssue::code)
            .contains(
                "UNKNOWN_FIELD",
                "PROHIBITED_KEY",
                "UNKNOWN_OUTPUT_TYPE",
                "UNKNOWN_DICTIONARY",
                "MODIFIER_DICTIONARY_ENTRY_INVALID",
                "INVALID_CONDITION_KEY"
            );
    }

    @Test
    void previewUsesCurrentResolverWithoutPrinterOrDeviceConfiguration() {
        PrintingDisplayRulePreviewRequest request = new PrintingDisplayRulePreviewRequest();
        request.store_id = 1L;
        request.content = StoreProfileCanonicalJson.parse(PrintingDisplayRuleDefaults.DEFAULT_CONTENT_JSON);
        request.item_sku = "traditional_beef_noodle";
        request.item_name_zh = "传统牛肉面";
        request.size_zh = "大碗";
        request.noodle_type_zh = "三细";
        request.spiciness_zh = "少辣";

        var response = service.preview(request);

        assertThat(response.fingerprint_sha256).isEqualTo(PrintingDisplayRuleDefaults.DEFAULT_FINGERPRINT);
        assertThat(response.grab_preview).contains("大").contains("传统牛肉面").contains("（少s）");
        assertThat(response.frontdesk_receipt_preview).contains("大碗").contains("牛肉面").contains("少辣");
        assertThat(response.hot_kitchen_preview).contains("传统牛肉面");
    }

    @Test
    void previewUsesIndependentHotKitchenDictionaryValues() {
        PrintingDisplayRulePreviewRequest request = new PrintingDisplayRulePreviewRequest();
        request.store_id = 1L;
        request.content = StoreProfileCanonicalJson.parse("""
            {
              "schema_version": "PRINTING_DISPLAY_RULES_V1",
              "outputs": ["GRAB", "FRONTDESK_RECEIPT", "HOT_KITCHEN"],
              "item_aliases": [
                {"item_sku": "traditional_beef_noodle", "outputs": {"GRAB": "牛G", "HOT_KITCHEN": "牛H", "FRONTDESK_RECEIPT": "牛R"}}
              ],
              "dictionaries": {
                "SIZE": [
                  {"semantic_code": "LARGE", "match_zh": ["大碗"], "outputs": {"GRAB": "大G", "HOT_KITCHEN": "大H", "FRONTDESK_RECEIPT_ZH": "大碗"}}
                ],
                "NOODLE_TYPE": [
                  {"semantic_code": "ER_XI", "match_zh": ["二细"], "outputs": {"GRAB": "二G", "HOT_KITCHEN": "二H"}}
                ],
                "SPICINESS": [
                  {"semantic_code": "LESS_SPICY", "match_zh": ["少辣"], "outputs": {"GRAB": "辣G", "HOT_KITCHEN": "辣H", "FRONTDESK_RECEIPT": "少辣"}}
                ],
                "MODIFIER_ADD": [["bok_choy", "+菜"]],
                "MODIFIER_REMOVE": []
              },
              "conditional_overrides": []
            }
            """);
        request.item_sku = "traditional_beef_noodle";
        request.item_name_zh = "传统牛肉面";
        request.size_zh = "大碗";
        request.noodle_type_zh = "二细";
        request.spiciness_zh = "少辣";

        var response = service.preview(request);

        assertThat(response.grab_preview).contains("大G").contains("牛G").contains("二G").contains("辣G");
        assertThat(response.hot_kitchen_preview).contains("大H").contains("牛H").contains("二H").contains("辣H");
        assertThat(response.hot_kitchen_preview).doesNotContain("大G", "牛G", "二G", "辣G");
    }
}
