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
@Table(name = "print_job_attempts")
public class PrintJobAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    public Long id;

    @Column(name = "print_job_id")
    public Long print_job_id;

    @Column(name = "printer_id")
    public Long printer_id;

    @Column(name = "attempt_number")
    public Integer attempt_number;

    @Column(name = "status")
    public String status;

    @Column(name = "error_message", columnDefinition = "text")
    public String error_message;

    @Column(name = "started_at")
    public LocalDateTime started_at;

    @Column(name = "finished_at")
    public LocalDateTime finished_at;
}
