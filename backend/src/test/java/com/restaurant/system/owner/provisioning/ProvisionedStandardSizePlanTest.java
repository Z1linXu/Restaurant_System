package com.restaurant.system.owner.provisioning;

import static org.assertj.core.api.Assertions.assertThat;

import com.restaurant.system.menu.entity.MenuItemOption;
import com.restaurant.system.owner.master.ChainMasterMenuOptionEntity;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProvisionedStandardSizePlanTest {

    @Test
    void promotesActiveLegacySizesToCanonicalStableIdentity() {
        ChainMasterMenuOptionEntity regular = option(
            "braised_beef_noodle",
            "braised_beef_noodle:OPT-042",
            "OPT-042",
            null,
            "Regular",
            true,
            42
        );
        ChainMasterMenuOptionEntity large = option(
            "braised_beef_noodle",
            "braised_beef_noodle:OPT-043",
            "OPT-043",
            null,
            "Large",
            true,
            43
        );

        var plan = ProvisionedStandardSizePlan.from(List.of(regular, large));

        assertThat(plan.get(regular.master_option_key).canonical()).isTrue();
        assertThat(plan.get(regular.master_option_key).size().code).isEqualTo("size_regular");
        assertThat(plan.get(regular.master_option_key).active()).isTrue();
        assertThat(plan.get(large.master_option_key).canonical()).isTrue();
        assertThat(plan.get(large.master_option_key).size().code).isEqualTo("size_large");
    }

    @Test
    void prefersExistingCanonicalCodeAndKeepsDuplicateMasterIdentityStable() {
        ChainMasterMenuOptionEntity legacy = option(
            "traditional_beef_noodle",
            "traditional_beef_noodle:OPT-035",
            "OPT-035",
            null,
            "Regular",
            false,
            35
        );
        ChainMasterMenuOptionEntity canonical = option(
            "traditional_beef_noodle",
            "traditional_beef_noodle:SIZE:size_regular",
            "OPT-008",
            "size_regular",
            "Regular",
            true,
            70
        );
        canonical.option_group = "SIZE";

        var plan = ProvisionedStandardSizePlan.from(List.of(legacy, canonical));

        assertThat(plan.get(canonical.master_option_key).canonical()).isTrue();
        assertThat(plan.get(canonical.master_option_key).active()).isTrue();
        assertThat(plan.get(legacy.master_option_key).canonical()).isFalse();
        assertThat(plan.get(legacy.master_option_key).active()).isFalse();
        assertThat(ProvisionedStandardSizePlan.stableLegacyCode(legacy, plan.get(legacy.master_option_key).size()))
            .isEqualTo("size_regular_legacy_opt_035");

        MenuItemOption canonicalRow = new MenuItemOption();
        OwnerStoreProvisioningMaterializer.applyOptionIdentity(
            canonicalRow,
            canonical,
            plan.get(canonical.master_option_key)
        );
        assertThat(canonicalRow.option_group).isEqualTo("SIZE");
        assertThat(canonicalRow.option_code).isEqualTo("size_regular");
        assertThat(canonicalRow.name_zh).isEqualTo("中碗");
        assertThat(canonicalRow.is_active).isTrue();

        MenuItemOption legacyRow = new MenuItemOption();
        OwnerStoreProvisioningMaterializer.applyOptionIdentity(
            legacyRow,
            legacy,
            plan.get(legacy.master_option_key)
        );
        assertThat(legacyRow.option_group).isNull();
        assertThat(legacyRow.option_code).isEqualTo("size_regular_legacy_opt_035");
        assertThat(legacyRow.is_active).isFalse();
    }

    @Test
    void inactiveLegacySizeDoesNotCreateCanonicalSizeConfiguration() {
        ChainMasterMenuOptionEntity inactive = option(
            "cold_noodle",
            "cold_noodle:OPT-260",
            "OPT-260",
            null,
            "Regular",
            false,
            260
        );

        var decision = ProvisionedStandardSizePlan.from(List.of(inactive)).get(inactive.master_option_key);

        assertThat(decision.canonical()).isFalse();
        assertThat(decision.active()).isFalse();
    }

    private ChainMasterMenuOptionEntity option(
        String productKey,
        String masterOptionKey,
        String optionRef,
        String code,
        String nameEn,
        boolean active,
        int sortOrder
    ) {
        ChainMasterMenuOptionEntity option = new ChainMasterMenuOptionEntity();
        option.master_product_key = productKey;
        option.master_option_key = masterOptionKey;
        option.option_ref = optionRef;
        option.option_type = "size";
        option.option_group = null;
        option.code = code;
        option.name_zh = "Regular".equals(nameEn) ? "标准份" : "大份";
        option.name_en = nameEn;
        option.default_active = active;
        option.sort_order = sortOrder;
        return option;
    }
}
