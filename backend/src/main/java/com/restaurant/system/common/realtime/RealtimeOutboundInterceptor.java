package com.restaurant.system.common.realtime;

import org.springframework.messaging.*;
import org.springframework.messaging.simp.*;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.stereotype.Component;

@Component
public class RealtimeOutboundInterceptor implements ExecutorChannelInterceptor {
    private final RealtimeAuthorization authorization;
    private final RealtimeSessionRegistry sessions;
    public RealtimeOutboundInterceptor(RealtimeAuthorization authorization,RealtimeSessionRegistry sessions) {
        this.authorization=authorization; this.sessions=sessions;
    }
    /** Re-check at executor delivery as well: a queued frame must not preserve revoked authority. */
    @Override public Message<?> beforeHandle(Message<?> message,MessageChannel channel,MessageHandler handler) {
        return authorize(message);
    }
    @Override public Message<?> preSend(Message<?> message,MessageChannel channel) { return authorize(message); }
    private Message<?> authorize(Message<?> message) {
        if (SimpMessageHeaderAccessor.getMessageType(message.getHeaders()) != SimpMessageType.MESSAGE) return message;
        String sessionId=SimpMessageHeaderAccessor.getSessionId(message.getHeaders());
        var identity=sessions.identity(sessionId);
        try {
            if (identity == null) return null;
            authorization.require(identity.bearer(),SimpMessageHeaderAccessor.getDestination(message.getHeaders()));
            return message;
        } catch (RuntimeException ex) { sessions.revoke(sessionId); return null; }
    }
}
