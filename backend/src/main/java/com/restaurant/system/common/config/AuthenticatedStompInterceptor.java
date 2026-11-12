package com.restaurant.system.common.config;

import com.restaurant.system.auth.service.TokenService;
import com.restaurant.system.common.auth.AuthenticatedUser;
import com.restaurant.system.common.auth.Capability;
import com.restaurant.system.common.auth.RoleCapabilityRegistry;
import com.restaurant.system.common.auth.StoreAccessService;
import com.restaurant.system.modules.ModuleKeys;
import com.restaurant.system.modules.StoreModuleAccessEvaluator;
import com.restaurant.system.user.repository.RoleRepository;
import com.restaurant.system.user.repository.StoreRepository;
import com.restaurant.system.user.repository.UserRepository;
import java.security.Principal;
import java.time.Clock;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;

/** Frontdesk-only migration adapter; denies publishing and all unapproved topics. */
final class AuthenticatedStompInterceptor implements ChannelInterceptor {
    private static final Pattern TOPIC = Pattern.compile("^/topic/stores/([1-9][0-9]*)/frontdesk/orders$");
    private final TokenService tokens;
    private final UserRepository users;
    private final RoleRepository roles;
    private final StoreAccessService stores;
    private final StoreRepository storeRepository;
    private final RoleCapabilityRegistry capabilities;
    private final StoreModuleAccessEvaluator modules;
    private final Clock clock;
    private final Map<String, SessionUser> sessions = new ConcurrentHashMap<>();

    AuthenticatedStompInterceptor(TokenService tokens, UserRepository users, RoleRepository roles,
        StoreAccessService stores, StoreRepository storeRepository, RoleCapabilityRegistry capabilities,
        StoreModuleAccessEvaluator modules, Clock clock) {
        this.tokens = tokens;
        this.users = users;
        this.roles = roles;
        this.stores = stores;
        this.storeRepository = storeRepository;
        this.capabilities = capabilities;
        this.modules = modules;
        this.clock = clock;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        var accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getSessionId() == null) throw denied();
        var command = accessor.getCommand();
        var authorization = accessor.getNativeHeader("Authorization");
        // Never leave credentials in a message that an error handler could log/echo.
        // Framework-generated DISCONNECT frames can already be immutable.
        if (authorization != null) accessor.removeNativeHeader("Authorization");
        String sessionId = accessor.getSessionId();
        if (command == StompCommand.DISCONNECT) {
            sessions.remove(sessionId);
            return message;
        }
        if (command == StompCommand.CONNECT || command == StompCommand.STOMP) {
            try {
                if (authorization == null || authorization.size() != 1
                    || !authorization.get(0).startsWith("Bearer ")) throw denied();
                var claims = tokens.parseAccessToken(authorization.get(0).substring(7).trim());
                var principal = new SessionUser(claims.userId(), claims.expiresAt());
                requireCurrentUser(principal);
                if (sessions.putIfAbsent(sessionId, principal) != null) throw denied();
                accessor.setUser(principal);
                return message;
            } catch (RuntimeException exception) {
                throw denied();
            }
        }
        var principal = sessions.get(sessionId);
        if (principal == null || principal.expiresAt() <= clock.instant().getEpochSecond()) throw denied();
        if (command == StompCommand.SUBSCRIBE) {
            requireTopic(principal, accessor.getDestination());
        } else if (command != StompCommand.UNSUBSCRIBE && accessor.getMessageType() != SimpMessageType.HEARTBEAT) {
            // In particular, clients cannot SEND to broker /topic destinations.
            throw denied();
        }
        return message;
    }

    Message<?> filterOutbound(Message<?> message) {
        var accessor = SimpMessageHeaderAccessor.wrap(message);
        if (accessor.getMessageType() != SimpMessageType.MESSAGE) return message;
        try {
            requireTopic(sessions.get(accessor.getSessionId()), accessor.getDestination());
            return message;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private void requireTopic(SessionUser principal, String destination) {
        var matcher = TOPIC.matcher(destination == null ? "" : destination);
        if (!matcher.matches()) throw denied();
        try {
            long storeId = Long.parseLong(matcher.group(1));
            var user = requireCurrentUser(principal);
            var store = storeRepository.findById(storeId).orElseThrow(AuthenticatedStompInterceptor::denied);
            if (!stores.canAccessStore(user, storeId)
                || !capabilities.hasCapability(stores.roleCodeForStore(user, store), Capability.ORDER_VIEW_ACTIVE)) {
                throw denied();
            }
            modules.requireOperationalCapability(storeId, ModuleKeys.ORDERING_POS);
        } catch (RuntimeException exception) {
            throw denied();
        }
    }

    private AuthenticatedUser requireCurrentUser(SessionUser principal) {
        if (principal == null || principal.expiresAt() <= clock.instant().getEpochSecond()) throw denied();
        var user = users.findById(principal.userId()).orElseThrow(AuthenticatedStompInterceptor::denied);
        if (!"active".equalsIgnoreCase(user.getStatus()) || user.getRole_id() == null) throw denied();
        var role = roles.findById(user.getRole_id()).orElseThrow(AuthenticatedStompInterceptor::denied);
        if (role.getCode() == null) throw denied();
        return new AuthenticatedUser(user.getId(), user.getStore_id(), user.getRole_id(),
            user.getUsername(), user.getFull_name(), role.getCode().toUpperCase(Locale.ROOT));
    }

    private static IllegalArgumentException denied() {
        return new IllegalArgumentException("Realtime access denied");
    }

    private record SessionUser(Long userId, long expiresAt) implements Principal {
        @Override public String getName() { return userId.toString(); }
    }
}
