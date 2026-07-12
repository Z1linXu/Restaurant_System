package com.restaurant.system.printing.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.restaurant.testing.PrintingRepositoryJpaTestApplication;
import com.restaurant.system.printing.PrintJobStatus;
import com.restaurant.system.printing.PrintModuleCode;
import com.restaurant.system.printing.entity.PrintJob;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false"
})
@ContextConfiguration(classes = PrintingRepositoryJpaTestApplication.class)
class PrintJobRepositoryTest {

    @Autowired
    private PrintJobRepository printJobRepository;

    @Test
    void findsOnlySuccessfulFullGrabSnapshotsInRequiredOrder() {
        LocalDateTime baseTime = LocalDateTime.of(2026, 7, 11, 10, 0);
        PrintJob olderTextSnapshot = saveJob("PRINTED", null, "FULL GRAB", null, baseTime);
        PrintJob newerEscPosSnapshot = saveJob("PRINTED", null, null, "base64-payload", baseTime.plusMinutes(5));
        PrintJob nullPrintedAtTextSnapshot = saveJob("PRINTED", null, "NO PRINTED AT", null, null);
        PrintJob failedMetadataOnly = saveJob("FAILED", null, null, null, baseTime.plusMinutes(10));
        failedMetadataOnly.payload_snapshot = "metadata-only";
        printJobRepository.save(failedMetadataOnly);
        saveJob("PRINTED", 77L, "UPDATED GRAB", null, baseTime.plusMinutes(9));
        saveJob("PENDING", null, "PENDING GRAB", null, baseTime.plusMinutes(8));
        saveJob("PRINTED", null, "   ", null, baseTime.plusMinutes(7));
        PrintJob printedMetadataOnly = saveJob("PRINTED", null, null, null, baseTime.plusMinutes(6));
        printedMetadataOnly.payload_snapshot = "metadata-only";
        printJobRepository.save(printedMetadataOnly);

        List<PrintJob> snapshots = printJobRepository.findReprintableFullGrabSnapshots(1L, 5L);

        assertEquals(
            List.of(newerEscPosSnapshot.id, olderTextSnapshot.id, nullPrintedAtTextSnapshot.id),
            snapshots.stream().map(job -> job.id).toList()
        );
    }

    private PrintJob saveJob(
        String status,
        Long orderUpdateBatchId,
        String renderedTextSnapshot,
        String escposPayloadBase64,
        LocalDateTime printedAt
    ) {
        PrintJob job = new PrintJob();
        job.organization_id = 1L;
        job.store_id = 1L;
        job.order_id = 5L;
        job.order_update_batch_id = orderUpdateBatchId;
        job.printer_id = 1L;
        job.module_code = PrintModuleCode.GRAB;
        job.receipt_type = PrintModuleCode.GRAB;
        job.status = status;
        job.rendered_text_snapshot = renderedTextSnapshot;
        job.escposPayloadBase64 = escposPayloadBase64;
        job.retry_count = 0;
        job.max_retry_count = 3;
        job.created_at = LocalDateTime.of(2026, 7, 11, 9, 0);
        job.updated_at = job.created_at;
        job.printed_at = printedAt;
        return printJobRepository.save(job);
    }
}
