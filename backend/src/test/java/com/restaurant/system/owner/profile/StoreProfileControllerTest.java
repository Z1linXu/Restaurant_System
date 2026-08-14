package com.restaurant.system.owner.profile;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.common.auth.AuthorizationService;
import com.restaurant.system.common.auth.ForbiddenException;
import com.restaurant.system.common.exception.GlobalExceptionHandler;
import com.restaurant.system.owner.profile.dto.StoreProfileArtifactResponse;
import com.restaurant.system.owner.profile.dto.StoreProfileSummaryResponse;
import com.restaurant.system.owner.profile.dto.StoreProfileVersionResponse;
import com.restaurant.system.owner.profile.dto.StoreProfileVersionSummaryResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class StoreProfileControllerTest {

    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private StoreProfileCatalogService catalogService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new StoreProfileController(authorizationService, catalogService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void profileListRequiresOwnerAndReturnsOnlySafeMetadata() throws Exception {
        when(catalogService.listProfiles()).thenReturn(List.of(new StoreProfileSummaryResponse(
            "ST_DENIS_CANONICAL_PROFILE",
            "St-Denis Canonical Profile",
            "safe",
            "PUBLISHED",
            "reviewed",
            List.of(new StoreProfileVersionSummaryResponse("v1", "PUBLISHED", "STORE_PROFILE_CONTRACT_V1", "a".repeat(64)))
        )));

        mockMvc.perform(get("/api/v1/store-profiles"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].profile_code").value("ST_DENIS_CANONICAL_PROFILE"))
            .andExpect(jsonPath("$.data[0].versions[0].fingerprint_sha256").value("a".repeat(64)));

        verify(authorizationService).requireOwner();
    }

    @Test
    void profileVersionReadRequiresOwner() throws Exception {
        when(catalogService.getVersion("ST_DENIS_CANONICAL_PROFILE", "v1")).thenReturn(new StoreProfileVersionResponse(
            "ST_DENIS_CANONICAL_PROFILE",
            "St-Denis Canonical Profile",
            "safe",
            "PUBLISHED",
            "reviewed",
            "v1",
            "PUBLISHED",
            "STORE_PROFILE_CONTRACT_V1",
            "b".repeat(64),
            true,
            List.of(),
            objectMapper.readTree("{\"profile_code\":\"ST_DENIS_CANONICAL_PROFILE\"}"),
            List.of(new StoreProfileArtifactResponse(
                "MENU_TEMPLATE",
                "ST_DENIS_MENU_TEMPLATE",
                "v1",
                "c".repeat(64),
                objectMapper.readTree("{}")
            ))
        ));

        mockMvc.perform(get("/api/v1/store-profiles/ST_DENIS_CANONICAL_PROFILE/versions/v1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.valid").value(true))
            .andExpect(jsonPath("$.data.artifacts[0].artifact_type").value("MENU_TEMPLATE"));

        verify(authorizationService).requireOwner();
    }

    @Test
    void profileReadRejectsNonOwner() throws Exception {
        when(authorizationService.requireOwner()).thenThrow(new ForbiddenException("owner required"));

        mockMvc.perform(get("/api/v1/store-profiles"))
            .andExpect(status().isForbidden());
    }
}
