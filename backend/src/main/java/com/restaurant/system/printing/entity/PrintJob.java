package com.restaurant.system.printing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Entity
@Table(name = "print_jobs")
public class PrintJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    public Long id;

    @Column(name = "organization_id")
    public Long organization_id;

    @Column(name = "store_id")
    public Long store_id;

    @Column(name = "order_id")
    public Long order_id;

    @Column(name = "order_update_batch_id")
    public Long order_update_batch_id;

    @Column(name = "dispatch_source_key")
    public String dispatchSourceKey;

    @Column(name = "printer_id")
    public Long printer_id;

    @Column(name = "module_code")
    public String module_code;

    @Column(name = "receipt_type")
    public String receipt_type;

    @Column(name = "status")
    public String status;

    @Column(name = "execution_mode")
    public String executionMode;

    @Column(name = "payload_snapshot", columnDefinition = "text")
    public String payload_snapshot;

    @Column(name = "rendered_text_snapshot", columnDefinition = "text")
    public String rendered_text_snapshot;

    @Column(name = "escpos_payload_base64", columnDefinition = "text")
    public String escposPayloadBase64;

    @Column(name = "error_message", columnDefinition = "text")
    public String error_message;

    @Column(name = "error_code")
    public String error_code;

    @Column(name = "retry_count")
    public Integer retry_count;

    @Column(name = "max_retry_count")
    public Integer max_retry_count;

    @Column(name = "requested_by_user_id")
    public Long requested_by_user_id;

    @Column(name = "claimed_by_device_id")
    public Long claimedByDeviceId;

    @Column(name = "claimed_at")
    public LocalDateTime claimedAt;

    @Column(name = "claim_expires_at")
    public LocalDateTime claimExpiresAt;

    @Column(name = "printed_by_device_id")
    public Long printedByDeviceId;

    @Column(name = "client_attempt_token")
    public String clientAttemptToken;

    @Column(name = "created_at")
    public LocalDateTime created_at;

    @Column(name = "updated_at")
    public LocalDateTime updated_at;

    @Column(name = "printed_at")
    public LocalDateTime printed_at;

    @Column(name = "failed_at")
    public LocalDateTime failed_at;

    @Column(name = "last_attempt_at")
    public LocalDateTime last_attempt_at;

    @Column(name = "attention_acknowledged_at")
    public LocalDateTime attentionAcknowledgedAt;

    @Column(name = "attention_acknowledged_by")
    public Long attentionAcknowledgedBy;

    @Column(name = "attention_acknowledgement_note", length = 500)
    public String attentionAcknowledgementNote;

    @Column(name = "attention_acknowledged_status", length = 32)
    public String attentionAcknowledgedStatus;

    @Column(name = "attention_acknowledged_retry_count")
    public Integer attentionAcknowledgedRetryCount;

    @Column(name = "attention_acknowledged_error_code", length = 128)
    public String attentionAcknowledgedErrorCode;
}
