package com.restaurant.system.order.service.impl;

import com.restaurant.system.menu.entity.MenuItem;
import com.restaurant.system.menu.entity.MenuItemOption;
import com.restaurant.system.menu.repository.MenuItemOptionRepository;
import com.restaurant.system.menu.repository.MenuItemRepository;
import com.restaurant.system.order.dto.CreateOrderItemOptionRequest;
import com.restaurant.system.order.dto.CreateOrderItemRequest;
import com.restaurant.system.order.dto.CreateOrderRequest;
import com.restaurant.system.order.dto.IdempotentOrderSubmitRequest;
import com.restaurant.system.order.dto.IdempotentOrderSubmitResponse;
import com.restaurant.system.order.dto.OrderResponse;
import com.restaurant.system.order.entity.OrderSubmissionRequest;
import com.restaurant.system.order.exception.OrderSubmissionException;
import com.restaurant.system.order.repository.OrderSubmissionRequestRepository;
import com.restaurant.system.order.service.IdempotentOrderSubmissionService;
import com.restaurant.system.order.service.OrderService;
import com.restaurant.system.order.service.OrderSubmissionHashService;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.repository.StoreRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdempotentOrderSubmissionServiceImpl implements IdempotentOrderSubmissionService {

    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final Set<String> REQUIRED_OPTION_TYPES = Set.of("size", "soup_base");

    private final OrderSubmissionRequestRepository submissionRepository;
    private final StoreRepository storeRepository;
    private final MenuItemRepository menuItemRepository;
    private final MenuItemOptionRepository menuItemOptionRepository;
    private final OrderSubmissionHashService hashService;
    private final OrderService orderService;

    public IdempotentOrderSubmissionServiceImpl(
        OrderSubmissionRequestRepository submissionRepository,
        StoreRepository storeRepository,
        MenuItemRepository menuItemRepository,
        MenuItemOptionRepository menuItemOptionRepository,
        OrderSubmissionHashService hashService,
        OrderService orderService
    ) {
        this.submissionRepository = submissionRepository;
        this.storeRepository = storeRepository;
        this.menuItemRepository = menuItemRepository;
        this.menuItemOptionRepository = menuItemOptionRepository;
        this.hashService = hashService;
        this.orderService = orderService;
    }

    @Override
    @Transactional
    public IdempotentOrderSubmitResponse submit(
        Long pathStoreId,
        IdempotentOrderSubmitRequest request,
        Long userId
    ) {
        validateRequestScope(pathStoreId, request);
        String idempotencyKey = normalizedKey(request);
        String payloadHash = hashService.hash(request);
        LocalDateTime now = LocalDateTime.now();
        submissionRepository.insertIfAbsent(
            request.organization_id,
            request.store_id,
            idempotencyKey,
            request.client_order_id.trim(),
            payloadHash,
            now
        );
        OrderSubmissionRequest submission = submissionRepository.findForUpdate(request.store_id, idempotencyKey)
            .orElseThrow(() -> new IllegalStateException("Idempotency record was not created"));

        if (!payloadHash.equals(submission.payloadHash)) {
            throw conflict(
                "IDEMPOTENCY_CONFLICT",
                "This idempotency key was already used with different order content"
            );
        }
        if (STATUS_COMPLETED.equals(submission.status) && submission.orderId != null) {
            return response(submission, orderService.getOrderDetail(submission.orderId), true);
        }

        Store store = validateStoreAndMenuRevision(request);
        validateMenuAndPrice(request);

        CreateOrderRequest createRequest = new CreateOrderRequest();
        createRequest.store_id = request.store_id;
        createRequest.created_by = userId;
        createRequest.order_type = request.order_type.trim();
        createRequest.table_no = trimToNull(request.table_no);
        createRequest.pickup_no = trimToNull(request.pickup_no);
        createRequest.items = request.items;

        OrderResponse order = orderService.createOrReplaceDraftAndSubmit(createRequest, request.server_order_id);
        submission.organizationId = store.organization_id;
        submission.orderId = order.id;
        submission.status = STATUS_COMPLETED;
        submission.errorCode = null;
        submission.completedAt = LocalDateTime.now();
        submission.updatedAt = submission.completedAt;
        submissionRepository.save(submission);
        return response(submission, order, false);
    }

    private void validateRequestScope(Long pathStoreId, IdempotentOrderSubmitRequest request) {
        if (!pathStoreId.equals(request.store_id)) {
            throw conflict("STORE_MISMATCH", "Request store does not match URL store");
        }
        if (request.items == null || request.items.isEmpty()) {
            throw badRequest("ORDER_EMPTY", "Order must contain at least one item");
        }
        String orderType = trimToNull(request.order_type);
        if (!"dine_in".equals(orderType) && !"pickup".equals(orderType)) {
            throw badRequest("ORDER_TYPE_INVALID", "order_type must be dine_in or pickup");
        }
        if ("dine_in".equals(orderType) && trimToNull(request.table_no) == null) {
            throw badRequest("ORDER_CONTEXT_INVALID", "table_no is required for dine-in orders");
        }
        if ("pickup".equals(orderType) && trimToNull(request.pickup_no) == null) {
            throw badRequest("ORDER_CONTEXT_INVALID", "pickup_no is required for pickup orders");
        }
    }

    private Store validateStoreAndMenuRevision(IdempotentOrderSubmitRequest request) {
        Store store = storeRepository.findById(request.store_id)
            .orElseThrow(() -> badRequest("STORE_MISMATCH", "Store does not exist"));
        if (!request.organization_id.equals(store.organization_id)) {
            throw conflict("STORE_MISMATCH", "Organization does not own this store");
        }
        if (!request.menu_revision.equals(store.menu_revision)) {
            throw conflict(
                "MENU_REVISION_STALE",
                "Menu changed since this order was prepared; refresh and review the order"
            );
        }
        return store;
    }

    private void validateMenuAndPrice(IdempotentOrderSubmitRequest request) {
        BigDecimal subtotal = BigDecimal.ZERO;
        for (CreateOrderItemRequest itemRequest : request.items) {
            MenuItem item = menuItemRepository.findById(itemRequest.menu_item_id)
                .orElseThrow(() -> badRequest("ITEM_DISABLED", "Menu item is no longer available"));
            if (!request.store_id.equals(item.store_id)) {
                throw conflict("STORE_MISMATCH", "Menu item belongs to another store");
            }
            if (!Boolean.TRUE.equals(item.is_active)) {
                throw conflict("ITEM_DISABLED", "Menu item is disabled: " + item.id);
            }
            if (Boolean.TRUE.equals(item.is_sold_out)) {
                throw conflict("ITEM_SOLD_OUT", "Menu item is sold out: " + item.id);
            }
            if (itemRequest.quantity == null || itemRequest.quantity < 1) {
                throw badRequest("ITEM_QUANTITY_INVALID", "Item quantity must be at least one");
            }

            List<MenuItemOption> availableOptions = menuItemOptionRepository.findAllByMenuItemIdOrdered(item.id);
            Set<String> requiredTypes = new HashSet<>();
            for (MenuItemOption option : availableOptions) {
                if (Boolean.TRUE.equals(option.is_active) && option.option_type != null) {
                    String optionType = option.option_type.toLowerCase(Locale.ROOT);
                    if (REQUIRED_OPTION_TYPES.contains(optionType)) requiredTypes.add(optionType);
                }
            }

            BigDecimal lineAmount = defaultAmount(item.base_price).multiply(BigDecimal.valueOf(itemRequest.quantity));
            Set<String> selectedTypes = new HashSet<>();
            Set<Long> selectedOptionIds = new HashSet<>();
            for (CreateOrderItemOptionRequest optionRequest : safeOptions(itemRequest)) {
                if (optionRequest.option_id == null || optionRequest.quantity == null || optionRequest.quantity < 1) {
                    throw badRequest("OPTION_INVALID", "Option id and positive quantity are required");
                }
                if (!selectedOptionIds.add(optionRequest.option_id)) {
                    throw badRequest("OPTION_INVALID", "Duplicate option id: " + optionRequest.option_id);
                }
                MenuItemOption option = menuItemOptionRepository.findById(optionRequest.option_id)
                    .orElseThrow(() -> badRequest("OPTION_INVALID", "Option does not exist: " + optionRequest.option_id));
                if (!Boolean.TRUE.equals(option.is_active)) {
                    throw conflict("OPTION_INVALID", "Option is disabled: " + option.id);
                }
                if (!item.id.equals(option.menu_item_id) && !"remove".equalsIgnoreCase(option.option_type)) {
                    throw badRequest("OPTION_INVALID", "Option does not belong to menu item: " + option.id);
                }
                if (option.option_type != null) selectedTypes.add(option.option_type.toLowerCase(Locale.ROOT));
                lineAmount = lineAmount.add(
                    defaultAmount(option.price_delta).multiply(BigDecimal.valueOf(optionRequest.quantity))
                );
            }
            if (!selectedTypes.containsAll(requiredTypes)) {
                throw conflict("REQUIRED_OPTION_MISSING", "A required size or soup option is missing");
            }
            subtotal = subtotal.add(lineAmount);
        }
        if (request.expected_subtotal_amount != null
            && request.expected_subtotal_amount.compareTo(subtotal) != 0) {
            throw conflict("PRICE_CHANGED", "Menu price changed; refresh and review the order");
        }
    }

    private List<CreateOrderItemOptionRequest> safeOptions(CreateOrderItemRequest item) {
        return item.options == null ? List.of() : item.options;
    }

    private BigDecimal defaultAmount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String normalizedKey(IdempotentOrderSubmitRequest request) {
        String clientOrderId = request.client_order_id.trim();
        String requestedKey = trimToNull(request.idempotency_key);
        if (requestedKey != null && !requestedKey.equals(clientOrderId)) {
            throw badRequest("IDEMPOTENCY_KEY_INVALID", "idempotency_key must match client_order_id");
        }
        return clientOrderId;
    }

    private IdempotentOrderSubmitResponse response(
        OrderSubmissionRequest submission,
        OrderResponse order,
        boolean replayed
    ) {
        IdempotentOrderSubmitResponse response = new IdempotentOrderSubmitResponse();
        response.client_order_id = submission.clientOrderId;
        response.idempotency_key = submission.idempotencyKey;
        response.payload_hash = submission.payloadHash;
        response.order_id = submission.orderId;
        response.replayed = replayed;
        response.order = order;
        return response;
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private OrderSubmissionException badRequest(String code, String message) {
        return new OrderSubmissionException(code, HttpStatus.BAD_REQUEST, message);
    }

    private OrderSubmissionException conflict(String code, String message) {
        return new OrderSubmissionException(code, HttpStatus.CONFLICT, message);
    }
}
