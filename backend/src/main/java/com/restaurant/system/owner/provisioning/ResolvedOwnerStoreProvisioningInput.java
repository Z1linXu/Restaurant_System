package com.restaurant.system.owner.provisioning;

import com.restaurant.system.owner.master.ChainMasterMenuCategoryEntity;
import com.restaurant.system.owner.master.ChainMasterMenuOptionEntity;
import com.restaurant.system.owner.master.ChainMasterMenuProductEntity;
import com.restaurant.system.owner.master.ChainMasterMenuVersionEntity;
import com.restaurant.system.owner.profile.StoreProfileArtifactEntity;
import com.restaurant.system.owner.profile.StoreProfileVersionEntity;
import java.util.List;

record ResolvedOwnerStoreProvisioningInput(
    OwnerStoreProvisioningCommand command,
    StoreProfileVersionEntity profileVersion,
    List<StoreProfileArtifactEntity> artifacts,
    ChainMasterMenuVersionEntity masterVersion,
    List<ChainMasterMenuCategoryEntity> categories,
    List<ChainMasterMenuProductEntity> products,
    List<ChainMasterMenuOptionEntity> options,
    String requestFingerprint
) {
}
