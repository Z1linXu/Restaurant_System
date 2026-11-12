package com.restaurant.system.common.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.restaurant.system.auth.service.TokenService;
import com.restaurant.system.common.auth.RoleCapabilityRegistry;
import com.restaurant.system.common.auth.StoreAccessService;
import com.restaurant.system.modules.StoreModuleAccessEvaluator;
import com.restaurant.system.user.entity.Role;
import com.restaurant.system.user.entity.User;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.repository.RoleRepository;
import com.restaurant.system.user.repository.UserRepository;
import com.restaurant.system.user.repository.StoreRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;

class AuthenticatedStompInterceptorTest {
    private final TokenService tokens = mock(TokenService.class);
    private final UserRepository users = mock(UserRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final StoreAccessService stores = mock(StoreAccessService.class);
    private final StoreRepository storeRepository = mock(StoreRepository.class);
    private final Store store = new Store();
    private final StoreModuleAccessEvaluator modules = mock(StoreModuleAccessEvaluator.class);
    private final Clock clock = mock(Clock.class);
    private final User user = new User();
    private AuthenticatedStompInterceptor guard;

    @BeforeEach void setup() {
        when(clock.instant()).thenReturn(Instant.ofEpochSecond(1000));
        when(tokens.parseAccessToken("valid")).thenReturn(new TokenService.AccessTokenClaims(1L, 1L, 1L, 1L, "OWNER", 900, 2000));
        user.setId(1L); user.setRole_id(1L); user.setStore_id(1L); user.setStatus("active");
        user.setUsername("owner"); user.setFull_name("Owner");
        Role role = new Role(); role.setId(1L); role.setCode("OWNER");
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(roles.findById(1L)).thenReturn(Optional.of(role));
        when(stores.canAccessStore(any(), eq(1L))).thenReturn(true);
        store.id = 1L;
        when(storeRepository.findById(1L)).thenReturn(Optional.of(store));
        when(stores.roleCodeForStore(any(), eq(store))).thenReturn("OWNER");
        guard = new AuthenticatedStompInterceptor(tokens, users, roles, stores, storeRepository,
            new RoleCapabilityRegistry(), modules, clock);
    }

    private Message<byte[]> frame(StompCommand command, String destination, String authorization) {
        var headers = StompHeaderAccessor.create(command);
        headers.setSessionId("session-1");
        if (destination != null) headers.setDestination(destination);
        if (authorization != null) headers.setNativeHeader("Authorization", authorization);
        headers.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }

    private Message<byte[]> outbound(String destination) {
        var headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setSessionId("session-1"); headers.setDestination(destination);
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }

    private void connect() { guard.preSend(frame(StompCommand.CONNECT, null, "Bearer valid"), null); }

    @Test void connectsWithVerifiedIdentityAndRemovesTokenHeader() {
        var message = frame(StompCommand.CONNECT, null, "Bearer valid");
        assertSame(message, guard.preSend(message, null));
        var headers = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        assertEquals("1", headers.getUser().getName());
        assertNull(headers.getNativeHeader("Authorization"));
        verify(tokens).parseAccessToken("valid");
    }

    @Test void rejectsAnonymousConnectionAndSubscription() {
        assertThrows(IllegalArgumentException.class, () -> guard.preSend(frame(StompCommand.CONNECT, null, null), null));
        assertThrows(IllegalArgumentException.class, () -> guard.preSend(frame(StompCommand.SUBSCRIBE, "/topic/stores/1/frontdesk/orders", null), null));
    }

    @Test void rejectsInvalidTokenWithoutLeavingItInErrorFrame() {
        when(tokens.parseAccessToken("bad")).thenThrow(new IllegalArgumentException("signature invalid"));
        var message = frame(StompCommand.CONNECT, null, "Bearer bad");
        var exception = assertThrows(IllegalArgumentException.class, () -> guard.preSend(message, null));
        assertEquals("Realtime access denied", exception.getMessage());
        assertNull(MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class).getNativeHeader("Authorization"));
    }

    @Test void rejectsInactiveUserAndExpiredToken() {
        user.setStatus("disabled");
        assertThrows(IllegalArgumentException.class, this::connect);
        user.setStatus("active"); when(clock.instant()).thenReturn(Instant.ofEpochSecond(2000));
        assertThrows(IllegalArgumentException.class, this::connect);
    }

    @Test void permitsOnlyAuthorizedOwnStoreOperationalTopic() {
        connect();
        var message = frame(StompCommand.SUBSCRIBE, "/topic/stores/1/frontdesk/orders", null);
        assertSame(message, guard.preSend(message, null));
        verify(modules).requireOperationalCapability(eq(1L), anyString());
        assertNotNull(guard.filterOutbound(outbound("/topic/stores/1/frontdesk/orders")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/topic/stores/2/frontdesk/orders", "/topic/stores/*/frontdesk/orders",
        "/topic/stores/1/**", "/topic/stores/1/kds/noodle-display", "/topic/stores/1/frontdesk/orders/extra",
        "/topic/stores/01/frontdesk/orders", "/topic/stores/99999999999999999999999/frontdesk/orders", "/topic/global"})
    void rejectsCrossStoreAndUnexpectedTopics(String destination) {
        connect();
        assertThrows(IllegalArgumentException.class, () -> guard.preSend(frame(StompCommand.SUBSCRIBE, destination, null), null));
        assertNull(guard.filterOutbound(outbound(destination)));
    }

    @Test void deniesPublishingEvenToAuthorizedTopic() {
        connect();
        assertThrows(IllegalArgumentException.class, () -> guard.preSend(frame(StompCommand.SEND, "/topic/stores/1/frontdesk/orders", null), null));
    }

    @Test void stopsDeliveryAfterExpiryOrMembershipRevocationOrDeactivation() {
        connect();
        assertNotNull(guard.filterOutbound(outbound("/topic/stores/1/frontdesk/orders")));
        when(clock.instant()).thenReturn(Instant.ofEpochSecond(2000));
        assertNull(guard.filterOutbound(outbound("/topic/stores/1/frontdesk/orders")));
        when(clock.instant()).thenReturn(Instant.ofEpochSecond(1000));
        when(stores.canAccessStore(any(), eq(1L))).thenReturn(false);
        assertNull(guard.filterOutbound(outbound("/topic/stores/1/frontdesk/orders")));
        when(stores.canAccessStore(any(), eq(1L))).thenReturn(true);
        user.setStatus("disabled");
        assertNull(guard.filterOutbound(outbound("/topic/stores/1/frontdesk/orders")));
    }

    @Test void honorsStoreRoleAndModuleRestrictions() {
        connect();
        when(stores.roleCodeForStore(any(), eq(store))).thenReturn("HOT_KITCHEN");
        assertThrows(IllegalArgumentException.class, () -> guard.preSend(frame(StompCommand.SUBSCRIBE, "/topic/stores/1/frontdesk/orders", null), null));
        when(stores.roleCodeForStore(any(), eq(store))).thenReturn("OWNER");
        doThrow(new IllegalArgumentException("module disabled")).when(modules).requireOperationalCapability(eq(1L), anyString());
        assertNull(guard.filterOutbound(outbound("/topic/stores/1/frontdesk/orders")));
    }

    @Test void frameworkGeneratedImmutableDisconnectStillClearsSession() {
        connect();
        var headers = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        headers.setSessionId("session-1");
        var message = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
        assertFalse(MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class).isMutable());
        assertSame(message, guard.preSend(message, null));
        assertNull(guard.filterOutbound(outbound("/topic/stores/1/frontdesk/orders")));
        assertSame(message, guard.preSend(message, null));
    }

    @Test void immutableHeartbeatDoesNotAttemptToMutateNativeHeaders() {
        connect();
        var headers = StompHeaderAccessor.createForHeartbeat();
        headers.setSessionId("session-1");
        var message = MessageBuilder.createMessage(new byte[] {'\n'}, headers.getMessageHeaders());
        assertFalse(MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class).isMutable());
        assertSame(message, guard.preSend(message, null));
    }

    @Test void disconnectIsIdempotentAndForgetsAuthorization() {
        connect(); guard.preSend(frame(StompCommand.DISCONNECT, null, null), null);
        guard.preSend(frame(StompCommand.DISCONNECT, null, null), null);
        assertNull(guard.filterOutbound(outbound("/topic/stores/1/frontdesk/orders")));
    }
}
