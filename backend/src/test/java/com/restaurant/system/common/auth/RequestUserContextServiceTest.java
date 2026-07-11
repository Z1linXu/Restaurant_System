package com.restaurant.system.common.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.restaurant.system.user.entity.Role;
import com.restaurant.system.user.entity.User;
import com.restaurant.system.user.repository.RoleRepository;
import com.restaurant.system.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RequestUserContextServiceTest {

    @Mock
    private HttpServletRequest request;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;

    private RequestUserContextService requestUserContextService;

    @BeforeEach
    void setUp() {
        requestUserContextService = new RequestUserContextService(request, userRepository, roleRepository, true);
    }

    @Test
    void resolvesAndCachesAuthenticatedUserFromServletRequest() {
        User user = new User();
        user.setId(7L);
        user.setStore_id(3L);
        user.setRole_id(2L);
        user.setUsername("owner");
        user.setFull_name("Owner User");
        user.setStatus("active");

        Role role = new Role();
        role.setId(2L);
        role.setCode("OWNER");
        role.setName("Owner");

        when(request.getAttribute(RequestUserContextService.AUTHENTICATED_USER_ATTRIBUTE)).thenReturn(null);
        when(request.getHeader("X-User-Id")).thenReturn("7");
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(roleRepository.findById(2L)).thenReturn(Optional.of(role));

        AuthenticatedUser authenticatedUser = requestUserContextService.getRequiredUser();

        assertEquals(7L, authenticatedUser.userId());
        assertEquals(3L, authenticatedUser.storeId());
        assertEquals(2L, authenticatedUser.roleId());
        assertEquals("owner", authenticatedUser.username());
        assertEquals("Owner User", authenticatedUser.fullName());
        assertEquals("OWNER", authenticatedUser.roleCode());
        verify(request).setAttribute(RequestUserContextService.AUTHENTICATED_USER_ATTRIBUTE, authenticatedUser);
    }
}
