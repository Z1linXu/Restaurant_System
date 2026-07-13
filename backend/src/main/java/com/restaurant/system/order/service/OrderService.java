package com.restaurant.system.order.service;

import com.restaurant.system.order.dto.CreateOrderRequest;
import com.restaurant.system.order.dto.OrderResponse;
import com.restaurant.system.order.dto.CreateOrderUpdateRequest;
import com.restaurant.system.order.dto.OrderUpdateResponse;
import com.restaurant.system.order.dto.CreateOrderItemRequest;
import com.restaurant.system.order.dto.FrontdeskOrderBoardResponse;
import com.restaurant.system.order.dto.UpdateDraftOrderHeaderRequest;
import com.restaurant.system.order.dto.UpdateDraftOrderItemQuantityRequest;
import com.restaurant.system.order.dto.UpdateDraftOrderItemRequest;
import java.util.List;

public interface OrderService {

    OrderResponse createOrder(CreateOrderRequest request);

    OrderResponse findOpenDraftOrder(Long storeId, String tableNo, String pickupNo);

    OrderResponse findOpenEditableOrder(Long storeId, String tableNo, String pickupNo);

    OrderResponse getOrderDetail(Long id);

    OrderResponse updateDraftOrderHeader(Long id, UpdateDraftOrderHeaderRequest request);

    OrderResponse addDraftOrderItem(Long id, CreateOrderItemRequest request);

    OrderResponse updateDraftOrderItemQuantity(Long id, Long itemId, UpdateDraftOrderItemQuantityRequest request);

    OrderResponse updateDraftOrderItem(Long id, Long itemId, UpdateDraftOrderItemRequest request);

    OrderResponse removeDraftOrderItem(Long id, Long itemId);

    OrderResponse submitOrder(Long id);

    OrderResponse createOrReplaceDraftAndSubmit(CreateOrderRequest request, Long serverOrderId);

    OrderUpdateResponse createOrderUpdate(Long id, CreateOrderUpdateRequest request, Long userId);

    List<OrderResponse> getActiveOrders(Long storeId, List<String> statuses, String orderType, String sortBy);

    List<FrontdeskOrderBoardResponse> getFrontdeskOrderBoard(
        Long storeId,
        List<String> statuses,
        String orderType,
        String tableNo,
        String pickupNo,
        String keyword
    );

    List<FrontdeskOrderBoardResponse> getFrontdeskOrderHistory(
        Long storeId,
        List<String> statuses,
        String orderType,
        String tableNo,
        String pickupNo,
        String keyword,
        Integer limit
    );

    List<FrontdeskOrderBoardResponse> getFrontdeskTodayOrderHistory(Long storeId, Integer limit);

    OrderResponse completeOrder(Long id);

    OrderResponse cancelOrder(Long id);
}
