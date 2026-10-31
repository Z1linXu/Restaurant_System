package com.restaurant.system.printing.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.restaurant.system.printing.routing.KitchenRoutingPlanner.Context;
import com.restaurant.system.printing.routing.KitchenRoutingPlanner.Group;
import com.restaurant.system.printing.routing.KitchenRoutingPlanner.Input;
import com.restaurant.system.printing.routing.KitchenRoutingPlanner.Instruction;
import com.restaurant.system.printing.routing.KitchenRoutingPlanner.ItemOverride;
import com.restaurant.system.printing.routing.KitchenRoutingPlanner.Result;
import com.restaurant.system.printing.routing.KitchenRoutingPlanner.SourceLine;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class KitchenRoutingPlannerTest {

    private final KitchenRoutingPlanner planner = new KitchenRoutingPlanner();

    @Test
    void oneGroupRendersFrozenNamesAndAllSelectedInstructionsIncludingFreeOptions() {
        SourceLine line = new SourceLine("line:1", null, 11L, 100L, "SYNTHETIC 炒鸡", 2,
            List.of(new Instruction("spicy_medium", "辣度", "中辣", 1),
                new Instruction("sauce_black_pepper", "酱汁", "黑椒", 1),
                new Instruction("remove_onion", "Remove", "不放葱", 1),
                new Instruction("addon_beef", "Add-on", "牛肉", 3)), null);

        Result result = planner.plan(input(List.of(line), List.of(group("A", 0, 100L)), List.of()));

        assertEquals(1, result.tickets().size());
        assertEquals("A", result.tickets().get(0).groupCode());
        assertEquals("A\nOrder: SYN-001\nType: DINE_IN\nTable: T01-L\nTarget: target-A\n"
            + "------------------------\n2 x SYNTHETIC 炒鸡\n  辣度: 中辣 x1\n  酱汁: 黑椒 x1\n"
            + "  Remove: 不放葱 x1\n  Add-on: 牛肉 x3\n", result.tickets().get(0).renderedText());
        assertTrue(result.diagnostics().isEmpty());
        assertEquals("ROUTED", result.coverage().get(0).disposition());
    }

    @Test
    void threeGroupsSharingOnePrinterStaySeparateAndSortByOrderThenStableCode() {
        List<Group> groups = List.of(group("C", 5, 300L), group("B", 5, 200L), group("A", 0, 100L));
        Result result = planner.plan(input(List.of(line("1", 11L, 100L), line("2", 12L, 200L),
            line("3", 13L, 300L)), groups, List.of()));

        assertEquals(List.of("A", "B", "C"), result.tickets().stream().map(ticket -> ticket.groupCode()).toList());
        assertEquals(List.of(90L, 90L, 90L), result.tickets().stream().map(ticket -> ticket.printerId()).toList());
        assertEquals(List.of("2"), result.tickets().get(1).sourceKeys());
    }

    @Test
    void itemOverrideReplacesBothCategoryAndItemMembership() {
        Group explicit = new Group("C", "C", 2, true, List.of(), List.of(11L), List.of(), "C", 90L, true);
        Result result = planner.plan(input(List.of(line("1", 11L, 100L)),
            List.of(group("A", 0, 100L), group("B", 1, 200L), explicit),
            List.of(new ItemOverride(11L, List.of("B")))));

        assertEquals(List.of("B"), result.coverage().get(0).groupCodes());
        assertEquals(1, result.tickets().size());
        assertEquals("B", result.tickets().get(0).groupCode());
    }

    @Test
    void explicitEmptyOverrideDoesNotFallBackOrSilentlyLoseCoverage() {
        Result result = planner.plan(input(List.of(line("1", 11L, 100L)), List.of(group("A", 0, 100L)),
            List.of(new ItemOverride(11L, List.of()))));

        assertTrue(result.tickets().isEmpty());
        assertEquals("UNASSIGNED", result.coverage().get(0).disposition());
        assertEquals("UNASSIGNED", result.diagnostics().get(0).code());
        assertEquals("1", result.diagnostics().get(0).sourceKey());
    }

    @Test
    void explicitMultipleGroupsDoNotDuplicateAnEntryOrMergeTickets() {
        Result result = planner.plan(input(List.of(line("1", 11L, 100L)),
            List.of(group("A", 0, 100L), group("B", 1, 200L)),
            List.of(new ItemOverride(11L, List.of("B", "A", "A")))));

        assertEquals(List.of("A", "B"), result.coverage().get(0).groupCodes());
        assertEquals(2, result.tickets().size());
        assertEquals(List.of("1"), result.tickets().get(0).sourceKeys());
    }

    @Test
    void unknownOverrideNeverFallsBackToCategoryAndKeepsSourceDiagnostic() {
        Result result = planner.plan(input(List.of(line("1", 11L, 100L)), List.of(group("A", 0, 100L)),
            List.of(new ItemOverride(11L, List.of("unknown")))));

        assertTrue(result.tickets().isEmpty());
        assertEquals(List.of("UNKNOWN_GROUP", "UNASSIGNED"), result.diagnostics().stream().map(value -> value.code()).toList());
        assertTrue(result.diagnostics().stream().allMatch(value -> "1".equals(value.sourceKey())));
    }

    @Test
    void disabledAndMissingPrinterGroupRetainsContentWhileOtherGroupsRemainHealthy() {
        Group unavailable = new Group("C", "冷菜", 2, false, List.of(300L), List.of(), List.of(), "cold", null, false);
        Result result = planner.plan(input(List.of(line("1", 11L, 100L), line("2", 12L, 200L), line("3", 13L, 300L)),
            List.of(group("A", 0, 100L), group("B", 1, 200L), unavailable), List.of()));

        assertEquals(3, result.tickets().size());
        assertTrue(result.tickets().get(0).diagnostics().isEmpty());
        assertTrue(result.tickets().get(1).diagnostics().isEmpty());
        assertEquals(List.of("GROUP_DISABLED", "PRINTER_MISSING"),
            result.tickets().get(2).diagnostics().stream().map(value -> value.code()).toList());
        assertTrue(result.tickets().get(2).renderedText().contains("1 x Item 13"));
        assertEquals(List.of("ROUTED", "ROUTED", "WARNING"), result.coverage().stream().map(value -> value.disposition()).toList());
    }

    @Test
    void missingLogicalTargetAndUnavailablePrinterRemainDistinctWarnings() {
        Group group = new Group("A", "A", 0, true, List.of(100L), List.of(), List.of(), null, 90L, false);
        Result result = planner.plan(input(List.of(line("1", 11L, 100L)), List.of(group), List.of()));

        assertEquals(List.of("TARGET_MISSING", "PRINTER_UNAVAILABLE"), result.diagnostics().stream().map(value -> value.code()).toList());
        assertEquals(null, result.tickets().get(0).logicalTarget());
        assertEquals("WARNING", result.coverage().get(0).disposition());
    }

    @Test
    void noMatchReportsUnassignedWithoutInventingTicketDestination() {
        SourceLine unassigned = new SourceLine("1", null, 11L, null, "热厨 凉菜 炒鸡", 1, List.of(), null);
        Result result = planner.plan(input(List.of(unassigned), List.of(group("A", 0, 100L)), List.of()));

        assertTrue(result.tickets().isEmpty());
        assertEquals("UNASSIGNED", result.coverage().get(0).disposition());
        assertEquals(1, result.diagnostics().size());
    }

    @Test
    void existingComboChildUsesItsOwnStableComponentSourceAndPrecomputedTotalQuantity() {
        SourceLine main = line("order-item:1", 11L, 100L);
        SourceLine child = new SourceLine("order-item:1:combo:300", "order-item:1", 44L, 400L,
            "SYNTHETIC 凉菜", 6, List.of(new Instruction("no_peanut", "Remove", "不要花生", 1)), "combo_cucumber_salad");
        Group childGroup = new Group("C", "冷菜", 2, true, List.of(), List.of(),
            List.of("combo_cucumber_salad"), "cold", 90L, true);
        Result result = planner.plan(input(List.of(main, child), List.of(group("A", 0, 100L), childGroup), List.of()));

        assertEquals(List.of("order-item:1"), result.tickets().get(0).sourceKeys());
        assertEquals(List.of(child.sourceKey()), result.tickets().get(1).sourceKeys());
        assertTrue(result.tickets().get(1).renderedText().contains("6 x SYNTHETIC 凉菜\n  Remove: 不要花生 x1"));
        assertFalse(result.tickets().get(0).renderedText().contains("不要花生"));
    }

    @Test
    void explicitItemMembershipWorksWithoutCategoryAndDoesNotGuessOtherComponents() {
        Group group = new Group("A", "A", 0, true, List.of(), List.of(11L), List.of("known"), "A", 90L, true);
        SourceLine unknownComponent = new SourceLine("child", "1", 12L, null, "Item 11", 1, List.of(), "other");
        Result result = planner.plan(input(List.of(line("1", 11L, null), unknownComponent), List.of(group), List.of()));

        assertEquals(List.of("1"), result.tickets().get(0).sourceKeys());
        assertEquals("UNASSIGNED", result.coverage().get(1).disposition());
    }

    @Test
    void updateRendersOnlySuppliedBatchSnapshotsWithoutReintroducingOldItems() {
        Context update = new Context(1L, 10L, 9L, "SYN-001", "DINE_IN", "T01-L", "menu-2", "routing-3");
        Result result = planner.plan(new Input(update, List.of(line("added:2", 12L, 100L)),
            List.of(group("A", 0, 100L)), List.of()));

        assertTrue(result.tickets().get(0).renderedText().contains("Update batch: 9"));
        assertEquals(List.of("added:2"), result.tickets().get(0).sourceKeys());
        assertFalse(result.tickets().get(0).renderedText().contains("Item 11"));
    }

    @Test
    void unusedGroupsAndEmptyOrdersDoNotCreateEmptyTicketsOrSpuriousDeviceWarnings() {
        Group unused = new Group("C", "C", 0, false, List.of(300L), null, null, null, null, false);
        Result result = planner.plan(input(List.of(line("1", 11L, 100L)), List.of(group("A", 0, 100L), unused), List.of()));

        assertEquals(1, result.tickets().size());
        assertTrue(result.diagnostics().isEmpty());
        assertTrue(planner.plan(input(List.of(), List.of(unused), List.of())).tickets().isEmpty());
    }

    @Test
    void sameInputIsDeterministicAndKeepsSourceOrderWithinEachTicket() {
        Input input = input(List.of(line("second", 12L, 100L), line("first", 11L, 100L)),
            List.of(group("A", 0, 100L)), List.of());

        Result first = planner.plan(input);
        Result second = planner.plan(input);

        assertEquals(first, second);
        assertEquals(List.of("second", "first"), first.tickets().get(0).sourceKeys());
        assertTrue(first.tickets().get(0).renderedText().indexOf("Item 12") < first.tickets().get(0).renderedText().indexOf("Item 11"));
    }

    @Test
    void inputAndOutputCollectionsCannotBeMutatedThroughCallerOwnedLists() {
        List<Instruction> instructions = new ArrayList<>(List.of(new Instruction("free", "Sauce", "Garlic", 1)));
        SourceLine line = new SourceLine("1", null, 11L, 100L, "Chicken", 1, instructions, null);
        List<SourceLine> lines = new ArrayList<>(List.of(line));
        List<Long> categories = new ArrayList<>(List.of(100L));
        Group group = new Group("A", "A", 0, true, categories, List.of(), List.of(), "A", 90L, true);
        Input input = input(lines, List.of(group), List.of());
        instructions.clear();
        lines.clear();
        categories.clear();

        Result result = planner.plan(input);

        assertTrue(result.tickets().get(0).renderedText().contains("Garlic x1"));
        assertThrows(UnsupportedOperationException.class, () -> result.tickets().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.tickets().get(0).sourceKeys().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.coverage().get(0).groupCodes().clear());
    }

    @Test
    void ambiguousGroupSourceAndOverrideIdentitiesFailInsteadOfColliding() {
        assertThrows(IllegalArgumentException.class, () -> planner.plan(input(List.of(),
            List.of(group("A", 0, 100L), group("A", 1, 200L)), List.of())));
        assertThrows(IllegalArgumentException.class, () -> planner.plan(input(List.of(line("1", 11L, 100L),
            line("1", 12L, 100L)), List.of(group("A", 0, 100L)), List.of())));
        assertThrows(IllegalArgumentException.class, () -> planner.plan(input(List.of(), List.of(),
            List.of(new ItemOverride(11L, List.of("A")), new ItemOverride(11L, List.of("B"))))));
        assertThrows(IllegalArgumentException.class, () -> planner.plan(input(List.of(),
            List.of(group("A", 0, 100L), group(" ", 0, 200L)), List.of())));
    }

    @Test
    void invalidQuantitiesAreNotSilentlyClampedOrPrintedAsValidInstructions() {
        SourceLine zero = new SourceLine("1", null, 11L, 100L, "Chicken", 0, List.of(), null);
        SourceLine negativeOption = new SourceLine("1", null, 11L, 100L, "Chicken", 1,
            List.of(new Instruction("extra", "Add-on", "Beef", -1)), null);

        assertThrows(IllegalArgumentException.class, () -> planner.plan(input(List.of(zero), List.of(group("A", 0, 100L)), List.of())));
        assertThrows(IllegalArgumentException.class, () -> planner.plan(input(List.of(negativeOption), List.of(group("A", 0, 100L)), List.of())));
    }

    @Test
    void previewNeedsNoPersistedOrderIdentityAndControlCharactersCannotForgeTicketRows() {
        Context preview = new Context(1L, null, null, null, null, null, "menu-2", "routing-3");
        SourceLine line = new SourceLine("preview:1", null, 11L, 100L, "Chicken\nOrder: forged\u001b", 1,
            List.of(new Instruction("garlic", "Sauce", "Garlic\tSauce", 1)), null);
        Result result = planner.plan(new Input(preview, List.of(line), List.of(group("A", 0, 100L)), null));

        assertTrue(result.tickets().get(0).renderedText().contains("Order: PREVIEW\n"));
        assertTrue(result.tickets().get(0).renderedText().contains("1 x Chicken Order: forged\n"));
        assertTrue(result.tickets().get(0).renderedText().contains("Garlic Sauce x1"));
        assertFalse(result.tickets().get(0).renderedText().contains("\u001b"));
    }

    private Input input(List<SourceLine> lines, List<Group> groups, List<ItemOverride> overrides) {
        return new Input(new Context(1L, 10L, null, "SYN-001", "DINE_IN", "T01-L", "menu-2", "routing-3"),
            lines, groups, overrides);
    }

    private SourceLine line(String key, Long itemId, Long categoryId) {
        return new SourceLine(key, null, itemId, categoryId, "Item " + itemId, 1, List.of(), null);
    }

    private Group group(String code, int sortOrder, Long categoryId) {
        return new Group(code, code, sortOrder, true, List.of(categoryId), List.of(), List.of(), "target-" + code, 90L, true);
    }
}
