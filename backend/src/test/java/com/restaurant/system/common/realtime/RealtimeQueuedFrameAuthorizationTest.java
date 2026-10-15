package com.restaurant.system.common.realtime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.restaurant.system.common.auth.CommercialSecurityFixture;
import com.restaurant.system.modules.StoreModuleAccessEvaluator;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.*;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.*;

class RealtimeQueuedFrameAuthorizationTest {
    @Test void queuedFrameIsDroppedAndTransportClosedAfterMembershipRevoked() throws Exception {
        var f = new CommercialSecurityFixture(); var sessions = new RealtimeSessionRegistry();
        var authorization = new RealtimeAuthorization(f.identities, f.access, f.capabilities, mock(StoreModuleAccessEvaluator.class));
        var outbound = new RealtimeOutboundInterceptor(authorization, sessions);
        var transport = mock(WebSocketSession.class); when(transport.getId()).thenReturn("queued-session"); when(transport.isOpen()).thenReturn(true);
        sessions.opened(transport); sessions.authenticate("queued-session",4L,f.bearer(4));
        var headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setSessionId("queued-session"); headers.setDestination("/topic/stores/10/frontdesk/orders");
        Message<?> frame = MessageBuilder.createMessage("synthetic-refresh", headers.getMessageHeaders());
        var channel = mock(MessageChannel.class); var handler = mock(MessageHandler.class);
        assertThat(outbound.preSend(frame,channel)).isSameAs(frame);
        f.member(4,10,"FRONTDESK",false);
        assertThat(outbound.beforeHandle(frame,channel,handler)).isNull();
        assertThat(sessions.identity("queued-session")).isNull();
        verify(transport).close(CloseStatus.POLICY_VIOLATION);
    }
}
