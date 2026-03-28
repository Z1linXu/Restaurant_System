package com.restaurant.system.common.realtime;

import java.time.LocalDateTime;
import java.util.List;

public class RealtimeUpdateMessage {

    public String event_type;
    public Long store_id;
    public Long order_id;
    public Long order_item_id;
    public String order_status;
    public String task_status;
    public String beverage_status;
    public Boolean is_modified_after_submit;
    public LocalDateTime happened_at;
    public List<String> suggested_topics;
}
