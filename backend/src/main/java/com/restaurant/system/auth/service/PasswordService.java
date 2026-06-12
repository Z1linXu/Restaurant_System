package com.restaurant.system.auth.service;

public interface PasswordService {

    String hashPassword(String rawPassword);

    boolean matches(String rawPassword, String passwordHash);
}
