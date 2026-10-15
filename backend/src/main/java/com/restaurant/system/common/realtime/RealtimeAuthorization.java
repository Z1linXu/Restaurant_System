package com.restaurant.system.common.realtime;

import com.restaurant.system.common.auth.*;
import com.restaurant.system.modules.ModuleKeys;
import com.restaurant.system.modules.StoreModuleAccessEvaluator;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class RealtimeAuthorization {
    private static final Pattern TOPIC=Pattern.compile("^/topic/stores/([1-9][0-9]*)/(.+)$");
    private static final Map<String, Capability[]> CAPABILITIES=Map.of(
        RealtimeTopics.FRONTDESK_ORDERS,new Capability[]{Capability.ORDER_VIEW_ACTIVE},
        RealtimeTopics.FRONTDESK_BEVERAGES,new Capability[]{Capability.BEVERAGE_VIEW_BOARD},
        RealtimeTopics.KDS_NOODLE,new Capability[]{Capability.KDS_NOODLE_VIEW},
        RealtimeTopics.KDS_HOT_KITCHEN,new Capability[]{Capability.KDS_HOT_VIEW},
        RealtimeTopics.KDS_PASS,new Capability[]{Capability.KDS_PASS_VIEW},
        RealtimeTopics.KDS_SERVING_SHELF,new Capability[]{Capability.SHELF_VIEW},
        RealtimeTopics.HISTORY,new Capability[]{Capability.ORDER_VIEW_HISTORY,Capability.KDS_HOT_VIEW,Capability.KDS_PASS_VIEW});
    private final BearerUserResolver identities;
    private final StoreAccessService access;
    private final RoleCapabilityRegistry roles;
    private final StoreModuleAccessEvaluator modules;
    public RealtimeAuthorization(BearerUserResolver identities, StoreAccessService access,
        RoleCapabilityRegistry roles, StoreModuleAccessEvaluator modules) {
        this.identities=identities; this.access=access; this.roles=roles; this.modules=modules;
    }
    public AuthenticatedUser identity(String bearer) { return identities.resolve(bearer); }
    public void require(String bearer, String destination) {
        AuthenticatedUser user=identity(bearer);
        var match=TOPIC.matcher(destination == null ? "" : destination);
        if (!match.matches() || !CAPABILITIES.containsKey(match.group(2))) throw new ForbiddenException("Realtime destination denied");
        Long storeId;
        try { storeId=Long.valueOf(match.group(1)); }
        catch (NumberFormatException ex) { throw new ForbiddenException("Realtime destination denied"); }
        AuthenticatedUser scoped=access.forStore(user, storeId);
        boolean allowed=java.util.Arrays.stream(CAPABILITIES.get(match.group(2)))
            .anyMatch(capability -> roles.hasCapability(scoped.roleCode(),capability));
        if (!allowed) throw new ForbiddenException("Realtime capability denied");
        String suffix=match.group(2);
        String module=suffix.startsWith("kds/") ? ModuleKeys.KDS
            : suffix.equals(RealtimeTopics.HISTORY) ? ModuleKeys.ORDER_HISTORY : ModuleKeys.ORDERING_POS;
        modules.requireCapability(storeId,module);
    }
}
