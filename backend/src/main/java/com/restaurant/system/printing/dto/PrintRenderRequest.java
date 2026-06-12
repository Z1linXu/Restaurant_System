package com.restaurant.system.printing.dto;

import com.restaurant.system.kitchen.entity.KitchenTask;
import com.restaurant.system.order.entity.Order;
import com.restaurant.system.order.entity.OrderItem;
import com.restaurant.system.order.entity.OrderItemOption;
import com.restaurant.system.user.entity.Store;
import java.time.LocalDateTime;
import java.util.List;

public class PrintRenderRequest {

    public String module_code;
    public Store store;
    public Order order;
    public List<OrderItem> order_items;
    public List<OrderItemOption> order_item_options;
    public List<KitchenTask> kitchen_tasks;
    public LocalDateTime happened_at;
}
