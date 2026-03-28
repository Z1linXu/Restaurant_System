package com.restaurant.system.kitchen.service;

import com.restaurant.system.kitchen.dto.KitchenTaskResponse;
import java.util.List;

public interface KitchenService {

    List<KitchenTaskResponse> getTasks(Long storeId, String stationCode);

    KitchenTaskResponse startTask(Long id);

    KitchenTaskResponse markReadyForPickup(Long id);

    KitchenTaskResponse markServed(Long id);

    KitchenTaskResponse completeTask(Long id);
}
