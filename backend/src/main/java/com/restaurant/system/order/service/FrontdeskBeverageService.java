package com.restaurant.system.order.service;

import com.restaurant.system.order.dto.FrontdeskBeverageItemResponse;
import java.util.List;

public interface FrontdeskBeverageService {

    List<FrontdeskBeverageItemResponse> getBeverageBoard(Long storeId, List<String> statuses);

    FrontdeskBeverageItemResponse startBeverage(Long orderItemId);

    FrontdeskBeverageItemResponse markBeverageReady(Long orderItemId);

    FrontdeskBeverageItemResponse markBeverageServed(Long orderItemId);

    FrontdeskBeverageItemResponse cancelBeverage(Long orderItemId);
}
