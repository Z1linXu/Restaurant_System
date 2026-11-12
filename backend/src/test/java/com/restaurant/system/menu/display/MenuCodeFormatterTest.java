package com.restaurant.system.menu.display;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MenuCodeFormatterTest {
    @Test void allPrintedCodesAreStableAcrossNamesAndLanguages() {
        int count = 0;
        for (var category : Map.of("A", 9, "B", 8, "C", 6, "D", 6, "E", 3).entrySet()) {
            for (int number = 1; number <= category.getValue(); number++) {
                String code = category.getKey() + number;
                assertEquals(code, MenuCodeFormatter.code("AN_" + code));
                assertEquals(code + " Renamed dish", MenuCodeFormatter.display("AN_" + code, "Renamed dish"));
                count++;
            }
        }
        assertEquals(32, count);
        assertEquals("C6 烤肉双拼饭", MenuCodeFormatter.display("AN_C6", "烤肉双拼饭"));
        assertEquals("A1 Soupe wonton", MenuCodeFormatter.display("AN_A1", "A1 A1 Soupe wonton"));
    }
    @Test void refillsUnknownCodesAndOtherMerchantsRemainUnchanged() {
        for (String sku : new String[] {"AN_REFILL_RICE", "AN_REFILL_NOODLES", "AN_C7", "AN_A01", "B1", "traditional_beef_noodle", "AN_E999999999999999"}) {
            assertNull(MenuCodeFormatter.code(sku));
            assertEquals("Original", MenuCodeFormatter.display(sku, "Original"));
        }
    }
}
