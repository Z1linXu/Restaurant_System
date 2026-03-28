package com.restaurant.system.user.repository;

import com.restaurant.system.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
