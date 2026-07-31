package com.restaurant.system.staging.bootstrap;

public interface StagingSyntheticBootstrapService {

    StagingSyntheticBootstrapResult bootstrap(
        StagingSyntheticBootstrapSpec spec,
        String rawPassword
    );
}
