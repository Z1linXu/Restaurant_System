package com.restaurant.system.printing.dto;

import com.restaurant.system.printing.entity.PrintJob;
import java.time.LocalDateTime;

public class PrintJobResponse {

    public Long id;
    public Long organization_id;
    public Long store_id;
    public Long order_id;
    public Long order_update_batch_id;
    public Long printer_id;
    public String printer_name;
    public String printer_endpoint;
    public String module_code;
    public String receipt_type;
    public String status;
    public String rendered_text_snapshot;
    public String error_message;
    public String error_code;
    public Integer retry_count;
    public Integer max_retry_count;
    public Long requested_by_user_id;
    public LocalDateTime created_at;
    public LocalDateTime updated_at;
    public LocalDateTime printed_at;
    public LocalDateTime failed_at;
    public LocalDateTime last_attempt_at;

    public static PrintJobResponse from(PrintJob job, String printerName, String printerEndpoint) {
        PrintJobResponse response = new PrintJobResponse();
        response.id = job.id;
        response.organization_id = job.organization_id;
        response.store_id = job.store_id;
        response.order_id = job.order_id;
        response.order_update_batch_id = job.order_update_batch_id;
        response.printer_id = job.printer_id;
        response.printer_name = printerName;
        response.printer_endpoint = printerEndpoint;
        response.module_code = job.module_code;
        response.receipt_type = job.receipt_type;
        response.status = job.status;
        response.rendered_text_snapshot = job.rendered_text_snapshot;
        response.error_message = job.error_message;
        response.error_code = job.error_code;
        response.retry_count = job.retry_count;
        response.max_retry_count = job.max_retry_count;
        response.requested_by_user_id = job.requested_by_user_id;
        response.created_at = job.created_at;
        response.updated_at = job.updated_at;
        response.printed_at = job.printed_at;
        response.failed_at = job.failed_at;
        response.last_attempt_at = job.last_attempt_at;
        return response;
    }
}
