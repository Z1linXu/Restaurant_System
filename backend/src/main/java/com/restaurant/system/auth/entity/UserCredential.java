package com.restaurant.system.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_credentials")
public class UserCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "user_id", nullable = false)
    public Long userId;

    @Column(name = "login_identifier", nullable = false, unique = true)
    public String loginIdentifier;

    @Column(name = "password_hash", nullable = false)
    public String passwordHash;

    @Column(name = "password_algorithm", nullable = false)
    public String passwordAlgorithm;

    @Column(name = "password_updated_at")
    public LocalDateTime passwordUpdatedAt;

    @Column(name = "is_active")
    public Boolean isActive;

    @Column(name = "created_at")
    public LocalDateTime createdAt;

    @Column(name = "updated_at")
    public LocalDateTime updatedAt;
}
