package com.restaurant.system.bootstrap;

import com.restaurant.system.auth.entity.UserCredential;
import com.restaurant.system.auth.repository.UserCredentialRepository;
import com.restaurant.system.auth.service.impl.PasswordServiceImpl;
import com.restaurant.system.common.config.ProductionSafetyConfig;
import com.restaurant.system.platform.entity.Organization;
import com.restaurant.system.platform.repository.OrganizationRepository;
import com.restaurant.system.user.entity.OrganizationMembership;
import com.restaurant.system.user.entity.Role;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.entity.StoreMembership;
import com.restaurant.system.user.entity.User;
import com.restaurant.system.user.repository.OrganizationMembershipRepository;
import com.restaurant.system.user.repository.RoleRepository;
import com.restaurant.system.user.repository.StoreMembershipRepository;
import com.restaurant.system.user.repository.StoreRepository;
import com.restaurant.system.user.repository.UserRepository;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories(basePackageClasses = {
    UserCredentialRepository.class,
    OrganizationRepository.class,
    StoreRepository.class,
    UserRepository.class,
    RoleRepository.class,
    OrganizationMembershipRepository.class,
    StoreMembershipRepository.class
})
@EntityScan(basePackageClasses = {
    UserCredential.class,
    Organization.class,
    Store.class,
    User.class,
    Role.class,
    OrganizationMembership.class,
    StoreMembership.class
})
@Import({PasswordServiceImpl.class, ProductionSafetyConfig.class})
public class ProductionAdminBootstrapApplication {
}
