package com.restaurant.system.staging.menu;

public interface StagingSyntheticSourceMenuService {

    StagingSyntheticSourceMenuResult plan(StagingSyntheticSourceMenuSpec spec);

    StagingSyntheticSourceMenuResult apply(StagingSyntheticSourceMenuSpec spec);
}
