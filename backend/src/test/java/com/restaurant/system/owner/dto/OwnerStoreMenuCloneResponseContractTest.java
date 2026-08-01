package com.restaurant.system.owner.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class OwnerStoreMenuCloneResponseContractTest {

    @Test
    void publicReplayContractContainsOnlyDurableSummaryAndSafeEvidence() throws Exception {
        Set<String> publicFields = Arrays.stream(OwnerStoreMenuCloneResponse.class.getFields())
            .map(Field::getName)
            .collect(Collectors.toSet());

        assertThat(publicFields).containsExactlyInAnyOrder(
            "clone_request_id",
            "organization_id",
            "source_store_id",
            "target_store_id",
            "profile_code",
            "source_menu_revision",
            "target_revision_before",
            "target_revision_after",
            "status",
            "replayed",
            "created",
            "result_code",
            "warnings"
        );
        assertThat(publicFields).doesNotContain(
            "category_ids_by_code",
            "station_ids_by_code",
            "item_ids_by_sku",
            "option_ids"
        );

        OwnerStoreMenuCloneResponse response = new OwnerStoreMenuCloneResponse();
        response.result_code = "MENU_CLONE_COMPLETED";
        response.warnings = List.of("SOURCE_OPTION_SKIPPED");

        String json = new ObjectMapper().writeValueAsString(response);

        assertThat(json).contains("\"result_code\":\"MENU_CLONE_COMPLETED\"");
        assertThat(json).contains("\"warnings\":[\"SOURCE_OPTION_SKIPPED\"]");
        assertThat(json).doesNotContain("ids_by", "option_ids");
    }
}
