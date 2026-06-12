package com.restaurant.system.auth.repository;

import com.restaurant.system.auth.entity.UserCredential;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserCredentialRepository extends JpaRepository<UserCredential, Long> {

    Optional<UserCredential> findFirstByLoginIdentifierIgnoreCaseAndIsActiveTrue(String loginIdentifier);

    Optional<UserCredential> findFirstByLoginIdentifierIgnoreCase(String loginIdentifier);

    boolean existsByLoginIdentifierIgnoreCase(String loginIdentifier);
}
