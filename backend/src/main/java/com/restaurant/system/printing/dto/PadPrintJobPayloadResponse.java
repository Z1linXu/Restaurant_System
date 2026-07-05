package com.restaurant.system.printing.dto;

import com.restaurant.system.printing.entity.PrintJob;
import java.time.LocalDateTime;

public class PadPrintJobPayloadResponse {
    public Long job_id;
    public Long store_id;
    public Long order_id;
    public Long order_update_batch_id;
    public Long printer_id;
    public String printer_name;
    public String printer_host;
    public Integer printer_port;
    public String printer_endpoint;
    public Integer paper_width_mm;
    public String text_encoding;
    public Integer escpos_code_page;
    public Integer timeout_ms;
    public String module_code;
    public String receipt_type;
    public String rendered_text_snapshot;
    public String escpos_payload_base64;
    public Long claimed_by_device_id;
    public LocalDateTime claim_expires_at;
    public String client_attempt_token;

    public static PadPrintJobPayloadResponse from(PrintJob job) {
        PadPrintJobPayloadResponse response = new PadPrintJobPayloadResponse();
        response.job_id = job.id;
        response.store_id = job.store_id;
        response.order_id = job.order_id;
        response.order_update_batch_id = job.order_update_batch_id;
        response.printer_id = job.printer_id;
        response.module_code = job.module_code;
        response.receipt_type = job.receipt_type;
        response.rendered_text_snapshot = job.rendered_text_snapshot;
        response.escpos_payload_base64 = job.escposPayloadBase64;
        response.claimed_by_device_id = job.claimedByDeviceId;
        response.claim_expires_at = job.claimExpiresAt;
        response.client_attempt_token = job.clientAttemptToken;
        return response;
    }

    public static PadPrintJobPayloadResponse from(PrintJob job, com.restaurant.system.printing.entity.PrinterConfig printer) {
        PadPrintJobPayloadResponse response = from(job);
        if (printer != null) {
            response.printer_id = printer.id;
            response.printer_name = printer.name;
            response.printer_host = printer.ip_address;
            response.printer_port = printer.port == null ? 9100 : printer.port;
            response.printer_endpoint = printer.ip_address + ":" + response.printer_port;
            response.paper_width_mm = printer.paper_width_mm;
            response.text_encoding = printer.text_encoding;
            response.escpos_code_page = printer.escpos_code_page;
            response.timeout_ms = printer.timeout_ms;
        }
        return response;
    }
}
