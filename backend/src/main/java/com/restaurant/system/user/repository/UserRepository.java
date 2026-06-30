package com.restaurant.system.user.repository;

import com.restaurant.system.user.entity.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    @Query("select u from User u where u.store_id = :storeId")
    List<User> findAllByStore_id(@Param("storeId") Long storeId);

    @Query("select u from User u where lower(u.username) = lower(:username)")
    Optional<User> findFirstByUsernameIgnoreCase(@Param("username") String username);
}
