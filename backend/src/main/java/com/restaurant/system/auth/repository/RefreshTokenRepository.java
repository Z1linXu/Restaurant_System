package com.restaurant.system.auth.repository;

import com.restaurant.system.auth.entity.RefreshToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findFirstByTokenHash(String tokenHash);
}
