package com.restaurant.system.printing.routing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Pure ticket planning shared by preview and persisted-order integration. */
public final class KitchenRoutingPlanner {

    public Result plan(Input input) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(input.context(), "context");
        for (Group group : input.groups()) {
            requireText(group.groupCode(), "groupCode");
        }
        List<Group> groups = input.groups().stream()
            .sorted(Comparator.comparingInt(Group::sortOrder).thenComparing(Group::groupCode))
            .toList();
        Map<String, Group> byCode = new LinkedHashMap<>();
        Map<String, List<SourceLine>> ticketLines = new LinkedHashMap<>();
        Map<String, List<Diagnostic>> groupDiagnostics = new HashMap<>();
        for (Group group : groups) {
            if (byCode.putIfAbsent(group.groupCode(), group) != null) {
                throw new IllegalArgumentException("Duplicate groupCode: " + group.groupCode());
            }
            ticketLines.put(group.groupCode(), new ArrayList<>());
            groupDiagnostics.put(group.groupCode(), diagnose(group));
        }
        Map<Long, List<String>> overrides = new HashMap<>();
        for (ItemOverride override : input.itemOverrides()) {
            Objects.requireNonNull(override.menuItemId(), "override menuItemId");
            if (overrides.putIfAbsent(override.menuItemId(), override.groupCodes()) != null) {
                throw new IllegalArgumentException("Duplicate item override: " + override.menuItemId());
            }
        }

        List<Diagnostic> diagnostics = new ArrayList<>();
        List<Coverage> coverage = new ArrayList<>();
        Set<String> seenSources = new HashSet<>();
        for (SourceLine line : input.lines()) {
            requireText(line.sourceKey(), "sourceKey");
            if (!seenSources.add(line.sourceKey())) {
                throw new IllegalArgumentException("Duplicate sourceKey: " + line.sourceKey());
            }
            if (line.quantity() <= 0 || line.instructions().stream().anyMatch(value -> value.quantity() <= 0)) {
                throw new IllegalArgumentException("Quantities must be positive: " + line.sourceKey());
            }
            List<String> override = overrides.get(line.menuItemId());
            boolean warning = false;
            if (override != null) {
                for (String code : override.stream().distinct().toList()) {
                    if (!byCode.containsKey(code)) {
                        diagnostics.add(new Diagnostic("UNKNOWN_GROUP", "Item override references unknown group: " + code, line.sourceKey()));
                        warning = true;
                    }
                }
            }
            List<String> matched = new ArrayList<>();
            for (Group group : groups) {
                boolean selected = override != null
                    ? override.contains(group.groupCode())
                    : matches(group, line);
                if (selected) {
                    matched.add(group.groupCode());
                    ticketLines.get(group.groupCode()).add(line);
                    warning |= !groupDiagnostics.get(group.groupCode()).isEmpty();
                }
            }
            if (matched.isEmpty()) {
                diagnostics.add(new Diagnostic("UNASSIGNED", "No kitchen ticket group is assigned to " + text(line.name(), line.sourceKey()), line.sourceKey()));
            }
            coverage.add(new Coverage(line.sourceKey(), matched,
                matched.isEmpty() ? "UNASSIGNED" : warning ? "WARNING" : "ROUTED"));
        }

        List<Ticket> tickets = new ArrayList<>();
        for (Group group : groups) {
            List<SourceLine> lines = ticketLines.get(group.groupCode());
            if (lines.isEmpty()) {
                continue;
            }
            List<Diagnostic> ticketDiagnostics = groupDiagnostics.get(group.groupCode());
            diagnostics.addAll(ticketDiagnostics);
            tickets.add(new Ticket(group.groupCode(), group.name(), group.logicalTarget(), group.printerId(),
                render(input.context(), group, lines), lines.stream().map(SourceLine::sourceKey).toList(), ticketDiagnostics));
        }
        return new Result(tickets, diagnostics, coverage);
    }

    private boolean matches(Group group, SourceLine line) {
        return line.menuItemId() != null && group.itemIds().contains(line.menuItemId())
            || line.categoryId() != null && group.categoryIds().contains(line.categoryId())
            || line.componentCode() != null && group.componentCodes().contains(line.componentCode());
    }

    private List<Diagnostic> diagnose(Group group) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        String name = text(group.name(), group.groupCode());
        if (!group.enabled()) {
            diagnostics.add(new Diagnostic("GROUP_DISABLED", "Kitchen ticket group is disabled: " + name, null));
        }
        if (group.logicalTarget() == null || group.logicalTarget().isBlank()) {
            diagnostics.add(new Diagnostic("TARGET_MISSING", "No logical target is bound to " + name, null));
        }
        if (group.printerId() == null) {
            diagnostics.add(new Diagnostic("PRINTER_MISSING", "No printer is bound to " + name, null));
        } else if (!group.printerAvailable()) {
            diagnostics.add(new Diagnostic("PRINTER_UNAVAILABLE", "Assigned printer is unavailable for " + name, null));
        }
        return List.copyOf(diagnostics);
    }

    private String render(Context context, Group group, List<SourceLine> lines) {
        StringBuilder content = new StringBuilder(text(group.name(), group.groupCode())).append('\n');
        String order = context.orderNumber() == null || context.orderNumber().isBlank()
            ? context.orderId() == null ? "PREVIEW" : context.orderId().toString()
            : context.orderNumber();
        content.append("Order: ").append(text(order, "PREVIEW")).append('\n');
        if (context.batchId() != null) {
            content.append("Update batch: ").append(context.batchId()).append('\n');
        }
        if (context.orderType() != null && !context.orderType().isBlank()) {
            content.append("Type: ").append(text(context.orderType(), "")).append('\n');
        }
        if (context.tableLabel() != null && !context.tableLabel().isBlank()) {
            content.append("Table: ").append(text(context.tableLabel(), "")).append('\n');
        }
        content.append("Target: ").append(text(group.logicalTarget(), "UNASSIGNED")).append('\n');
        content.append("------------------------\n");
        for (SourceLine line : lines) {
            content.append(line.quantity()).append(" x ").append(text(line.name(), line.sourceKey())).append('\n');
            for (Instruction instruction : line.instructions()) {
                content.append("  ");
                if (instruction.group() != null && !instruction.group().isBlank()) {
                    content.append(text(instruction.group(), "")).append(": ");
                }
                content.append(text(instruction.name(), instruction.code())).append(" x").append(instruction.quantity()).append('\n');
            }
        }
        return content.toString();
    }

    private static String text(String value, String fallback) {
        String selected = value == null || value.isBlank() ? fallback : value;
        return selected == null ? "" : selected.replaceAll("[\\p{Cntrl}\\p{Cf}]", " ").strip();
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    private static <T> List<T> copy(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    public record Context(Long storeId, Long orderId, Long batchId, String orderNumber, String orderType,
                          String tableLabel, String menuRevision, String routingRevision) {
    }

    public record Instruction(String code, String group, String name, int quantity) {
    }

    public record SourceLine(String sourceKey, String parentSourceKey, Long menuItemId, Long categoryId,
                             String name, int quantity, List<Instruction> instructions, String componentCode) {
        public SourceLine {
            instructions = copy(instructions);
        }
    }

    public record Group(String groupCode, String name, int sortOrder, boolean enabled, List<Long> categoryIds,
                        List<Long> itemIds, List<String> componentCodes, String logicalTarget,
                        Long printerId, boolean printerAvailable) {
        public Group {
            categoryIds = copy(categoryIds);
            itemIds = copy(itemIds);
            componentCodes = copy(componentCodes);
        }
    }

    public record ItemOverride(Long menuItemId, List<String> groupCodes) {
        public ItemOverride {
            groupCodes = copy(groupCodes);
        }
    }

    public record Input(Context context, List<SourceLine> lines, List<Group> groups, List<ItemOverride> itemOverrides) {
        public Input {
            lines = copy(lines);
            groups = copy(groups);
            itemOverrides = copy(itemOverrides);
        }
    }

    public record Diagnostic(String code, String message, String sourceKey) {
    }

    public record Coverage(String sourceKey, List<String> groupCodes, String disposition) {
        public Coverage {
            groupCodes = copy(groupCodes);
        }
    }

    public record Ticket(String groupCode, String groupName, String logicalTarget, Long printerId,
                         String renderedText, List<String> sourceKeys, List<Diagnostic> diagnostics) {
        public Ticket {
            sourceKeys = copy(sourceKeys);
            diagnostics = copy(diagnostics);
        }
    }

    public record Result(List<Ticket> tickets, List<Diagnostic> diagnostics, List<Coverage> coverage) {
        public Result {
            tickets = copy(tickets);
            diagnostics = copy(diagnostics);
            coverage = copy(coverage);
        }
    }
}
