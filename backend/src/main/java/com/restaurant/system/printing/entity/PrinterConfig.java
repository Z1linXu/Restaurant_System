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
@Table(name = "printer_configs")
public class PrinterConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    public Long id;

    @Column(name = "store_id")
    public Long store_id;

    @Column(name = "name")
    public String name;

    @Column(name = "ip_address")
    public String ip_address;

    @Column(name = "port")
    public Integer port;

    @Column(name = "printer_type")
    public String printer_type;

    @Column(name = "text_encoding")
    public String text_encoding;

    @Column(name = "escpos_code_page")
    public Integer escpos_code_page;

    @Column(name = "font_size")
    public String font_size;

    @Column(name = "font_size_mode")
    public String font_size_mode;

    @Column(name = "enabled")
    public Boolean enabled;

    @Column(name = "paper_width_mm")
    public Integer paper_width_mm;

    @Column(name = "timeout_ms")
    public Integer timeout_ms;

    @Column(name = "last_successful_print_at")
    public LocalDateTime last_successful_print_at;

    @Column(name = "last_failed_print_at")
    public LocalDateTime last_failed_print_at;

    @Column(name = "last_error_message", columnDefinition = "text")
    public String last_error_message;

    @Column(name = "last_connection_success_at")
    public LocalDateTime last_connection_success_at;

    @Column(name = "last_connection_failed_at")
    public LocalDateTime last_connection_failed_at;

    @Column(name = "last_connection_error", columnDefinition = "text")
    public String last_connection_error;

    @Column(name = "created_at")
    public LocalDateTime created_at;

    @Column(name = "updated_at")
    public LocalDateTime updated_at;
}
