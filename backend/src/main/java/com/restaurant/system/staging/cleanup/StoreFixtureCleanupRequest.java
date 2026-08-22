package com.restaurant.system.staging.cleanup;

import java.util.ArrayList;
import java.util.List;

/**
 * Explicit target list for the bounded Staging synthetic/test fixture cleanup path.
 * This is not a general Store deletion request.
 */
public class StoreFixtureCleanupRequest {

    public List<Long> store_ids = new ArrayList<>();
    public List<Long> approved_owner_manual_store_ids = new ArrayList<>();
    public Boolean dry_run = Boolean.TRUE;
}
