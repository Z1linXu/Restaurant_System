package com.restaurant.system.owner.profile;

import static org.mockito.Mockito.*;
import java.util.Optional;

/** Explicit local compatibility opt-in; never used by application code or seeders. */
public final class SyntheticPrivateProfileFixture {
    public static StoreProfileRepository repository(Long organizationId) {
        var profiles = mock(StoreProfileRepository.class);
        var profile = new StoreProfileEntity();
        profile.profile_code = "ST_DENIS_CANONICAL_PROFILE";
        profile.owner_organization_id = organizationId;
        lenient().when(profiles.findByProfileCode(profile.profile_code)).thenReturn(Optional.of(profile));
        return profiles;
    }
}
