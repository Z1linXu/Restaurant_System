package com.restaurant.system.analytics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Entity
@Table(name = "analytics_alerts")
public class AnalyticsAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    public Long id;

    @Column(name = "organization_id")
    public Long organization_id;

    @Column(name = "store_id")
    public Long store_id;

    @Column(name = "alert_type")
    public String alert_type;

    @Column(name = "severity")
    public String severity;

    @Column(name = "title")
    public String title;

    @Column(name = "message")
    public String message;

    @Column(name = "metric_value")
    public BigDecimal metric_value;

    @Column(name = "comparison_value")
    public BigDecimal comparison_value;

    @Column(name = "is_resolved")
    public Boolean is_resolved;

    @Column(name = "created_at")
    public LocalDateTime created_at;

    @Column(name = "resolved_at")
    public LocalDateTime resolved_at;
}
