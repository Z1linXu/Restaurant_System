package com.restaurant.system.printing.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.restaurant.system.printing.entity.PrintJob;
import org.junit.jupiter.api.Test;

class PrintJobResponseTest {

    @Test
    void mapsCloudPrivatePrinterBlockedToOperatorMessage() {
        PrintJobResponse response = PrintJobResponse.from(job("GRAB", "GRAB", "FAILED", "CLOUD_PRIVATE_PRINTER_BLOCKED"), null, null);

        assertEquals(
            "Cloud server cannot directly connect to this LAN printer. Use PAD_DIRECT, MOCK, DISABLED, or a local print bridge.",
            response.operator_message
        );
    }

    @Test
    void mapsPrintingDisabledCancelledToNonFailureOperatorMessage() {
        PrintJobResponse response = PrintJobResponse.from(job("FRONTDESK_RECEIPT", "FRONTDESK_RECEIPT", "CANCELLED", "PRINTING_DISABLED"), null, null);

        assertEquals(
            "Automatic printing is disabled for this store. Orders are saved, but no physical ticket was printed.",
            response.operator_message
        );
    }

    @Test
    void mapsAssignmentMissingWithModuleContext() {
        PrintJobResponse response = PrintJobResponse.from(job("GRAB", "GRAB_UPDATE", "FAILED", "ASSIGNMENT_MISSING"), null, null);

        assertEquals(
            "No printer is assigned for Kitchen update ticket. Configure the printer assignment, then reprint.",
            response.operator_message
        );
    }

    @Test
    void mapsConnectionFailureToImmediateReprintMessage() {
        PrintJobResponse response = PrintJobResponse.from(job("FRONTDESK_RECEIPT", "FRONTDESK_RECEIPT_UPDATE", "FAILED", "CONNECTION_FAILED"), null, null);

        assertEquals(
            "Frontdesk update receipt failed to print. Check printer power, IP/network, and reprint immediately.",
            response.operator_message
        );
    }

    @Test
    void printedJobDoesNotNeedOperatorMessage() {
        PrintJobResponse response = PrintJobResponse.from(job("GRAB", "GRAB", "PRINTED", null), null, null);

        assertNull(response.operator_message);
    }

    @Test
    void unknownFailedJobUsesFallbackMessage() {
        PrintJobResponse response = PrintJobResponse.from(job("BAR", "BAR", "FAILED", "SOMETHING_NEW"), null, null);

        assertTrue(response.operator_message.contains("BAR failed to print"));
    }

    private PrintJob job(String moduleCode, String receiptType, String status, String errorCode) {
        PrintJob job = new PrintJob();
        job.module_code = moduleCode;
        job.receipt_type = receiptType;
        job.status = status;
        job.error_code = errorCode;
        return job;
    }
}
