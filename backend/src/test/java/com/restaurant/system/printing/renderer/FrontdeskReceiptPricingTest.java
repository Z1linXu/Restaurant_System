package com.restaurant.system.printing.renderer;

import static org.assertj.core.api.Assertions.assertThat;

import com.restaurant.system.order.entity.Order;
import com.restaurant.system.order.entity.OrderItem;
import com.restaurant.system.printing.dto.PrintRenderRequest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FrontdeskReceiptPricingTest {
    @ParameterizedTest
    @CsvSource({
        "2,37.98,18.99,18.99",
        "2,47.98,23.99,23.99",
        "2,51.96,25.98,25.98",
        "2,29.98,14.99,14.99",
        "2,35.99,18.00,17.99",
        "2,-0.01,-0.01,0.00"
    })
    void splitsPersistedCentsWithoutReadingBaseOrCurrentMenu(int quantity, String total, String first, String second) {
        OrderItem item = new OrderItem();
        item.id = 1L;
        item.item_name_snapshot_zh = "牛肉面";
        item.quantity = quantity;
        item.unit_price = new BigDecimal("16.99");
        item.line_amount = new BigDecimal(total);
        Order order = new Order();
        order.subtotal_amount = item.line_amount;
        order.total_amount = item.line_amount;
        PrintRenderRequest request = new PrintRenderRequest();
        request.order = order;
        request.order_items = List.of(item);
        request.order_item_options = List.of();

        String rendered = new FrontdeskReceiptRenderer().render(request).replaceAll("\\[\\[/?[^]]+]]", "");
        List<String> amounts = rendered.lines().filter(line -> line.startsWith("$")).toList();

        assertThat(amounts).containsExactly("$" + first, "$" + second);
        assertThat(amounts.stream().map(line -> new BigDecimal(line.substring(1)))
            .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo(item.line_amount);
        assertThat(item.unit_price).isEqualByComparingTo("16.99");
        assertThat(item.line_amount).isEqualByComparingTo(total);
    }
}
