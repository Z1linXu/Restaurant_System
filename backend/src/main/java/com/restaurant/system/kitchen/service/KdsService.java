package com.restaurant.system.kitchen.service;

import com.restaurant.system.kitchen.dto.FrontdeskBeverageOrderResponse;
import com.restaurant.system.kitchen.dto.KdsOrderGroupResponse;
import com.restaurant.system.kitchen.dto.KdsTaskDisplayResponse;
import com.restaurant.system.kitchen.dto.ServingShelfItemResponse;
import java.util.List;

public interface KdsService {

    List<KdsTaskDisplayResponse> getNoodleDisplay(Long storeId, Integer limit);

    List<KdsTaskDisplayResponse> getHotKitchenDisplay(Long storeId);

    List<KdsOrderGroupResponse> getPassView(Long storeId);

    List<FrontdeskBeverageOrderResponse> getFrontdeskBeverageView(Long storeId);

    List<ServingShelfItemResponse> getServingShelfView(Long storeId);

    List<KdsOrderGroupResponse> getHistoryView(Long storeId, Integer limit, String stationCode);
}
