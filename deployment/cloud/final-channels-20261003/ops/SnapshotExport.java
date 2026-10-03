import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.restaurant.system.integration.ubereats.dto.UberFinancialSnapshot;
import java.util.ArrayList;
import java.util.List;

/** Operational stdin/stdout bridge to the exact application financial algorithm. */
public class SnapshotExport {
    private static final ObjectMapper JSON = new ObjectMapper();
    private record RawItem(JsonNode value, boolean removed) {}

    public static void main(String[] args) throws Exception {
        JsonNode inputs = JSON.readTree(System.in);
        if (!inputs.isArray() || inputs.size() > 1000) throw new IllegalArgumentException("BATCH_SHAPE_INVALID");
        ArrayNode outputs = JSON.createArrayNode();
        for (JsonNode input : inputs) {
            ObjectNode output = outputs.addObject();
            output.set("row_id", input.path("row_id"));
            try {
                JsonNode raw = input.path("response"), frozen = input.path("frozen");
                String orderId = input.path("order_id").asText(""), storeId = input.path("store_id").asText("");
                require(!orderId.isEmpty() && !storeId.isEmpty()
                    && orderId.equals(raw.path("id").asText()) && storeId.equals(raw.path("store").path("id").asText())
                    && orderId.equals(frozen.path("id").asText()) && storeId.equals(frozen.path("store_id").asText()), "ORDER_IDENTITY_MISMATCH");
                require(!frozen.path("placed_at").asText("").isEmpty()
                    && frozen.path("placed_at").equals(raw.path("placed_at")), "PLACED_TIME_MISMATCH");
                JsonNode charges = raw.path("payment").path("charges");
                require(charges.isObject() && !charges.isEmpty()
                    && charges.equals(frozen.path("financial_charges"))
                    && charges.equals(input.path("frozen_charges")), "FROZEN_CHARGES_MISMATCH");
                require(sameItems(frozen.path("items"), rawItems(raw.path("cart").path("items"), false), 0), "CART_IDENTITY_MISMATCH");
                output.put("status", "MATCHED");
                output.set("snapshot", JSON.valueToTree(UberFinancialSnapshot.capture(raw)));
            } catch (GuardFailure failure) {
                output.put("status", "BLOCKED"); output.put("reason", failure.getMessage());
            } catch (Exception failure) {
                output.put("status", "BLOCKED"); output.put("reason", "UNSUPPORTED_RESPONSE_SHAPE");
            }
        }
        // Contains only the new allowlisted financial DTO; no raw response/customer fields.
        System.out.print(JSON.writeValueAsString(outputs));
    }

    private static List<RawItem> rawItems(JsonNode items, boolean removed) {
        if (items.isNull() || items.isMissingNode()) return List.of();
        require(items.isArray() && items.size() <= 200, "CART_SHAPE_INVALID");
        List<RawItem> result = new ArrayList<>();
        for (JsonNode item : items) result.add(new RawItem(item, removed));
        return result;
    }

    // Identity projection follows UberEatsOrderNormalizer: selected items, then removed
    // items per group; removed quantity is one. No financial arithmetic is reproduced.
    private static boolean sameItems(JsonNode old, List<RawItem> fresh, int depth) {
        if (!old.isArray() || old.size() != fresh.size() || old.size() > 200 || depth > 8) return false;
        for (int n = 0; n < old.size(); n++) {
            JsonNode a = old.get(n), b = fresh.get(n).value();
            boolean removed = fresh.get(n).removed();
            if (!a.path("id").isTextual() || a.path("id").asText().isEmpty()
                || !a.path("id").equals(b.path("id")) || !a.path("quantity").isIntegralNumber()
                || !a.path("removed").isBoolean() || a.path("removed").asBoolean() != removed
                || (!removed && !b.path("quantity").isIntegralNumber())
                || a.path("quantity").asInt() != (removed ? 1 : b.path("quantity").asInt())) return false;
            JsonNode groups = b.path("selected_modifier_groups");
            if (!groups.isNull() && !groups.isMissingNode() && !groups.isArray()) return false;
            List<RawItem> modifiers = new ArrayList<>();
            if (groups.isArray()) for (JsonNode group : groups) {
                modifiers.addAll(rawItems(group.path("selected_items"), false));
                modifiers.addAll(rawItems(group.path("removed_items"), true));
            }
            if (!sameItems(a.path("modifiers"), modifiers, depth + 1)) return false;
        }
        return true;
    }

    private static void require(boolean condition, String reason) {
        if (!condition) throw new GuardFailure(reason);
    }
    private static class GuardFailure extends RuntimeException {
        GuardFailure(String reason) { super(reason); }
    }
}
