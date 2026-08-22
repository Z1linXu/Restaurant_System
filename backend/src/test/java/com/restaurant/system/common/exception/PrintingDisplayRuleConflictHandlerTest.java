package com.restaurant.system.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.restaurant.system.printing.rules.PrintingDisplayRuleConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class PrintingDisplayRuleConflictHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void printingRuleConstraintConflictReturnsStableHttp409BusinessError() {
        var response = handler.handlePrintingDisplayRuleConflictException(
            new PrintingDisplayRuleConflictException(
                "PRINTING_DISPLAY_RULE_DRAFT_CONFLICT",
                "Printing display rule draft conflicted with another revision update"
            )
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getErrorCode()).isEqualTo("PRINTING_DISPLAY_RULE_DRAFT_CONFLICT");
    }
}
