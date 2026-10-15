package com.restaurant.system.common.realtime;

import com.restaurant.system.common.auth.ForbiddenException;
import java.util.List;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.*;
import org.springframework.stereotype.Component;

@Component
public class RealtimeInboundInterceptor implements ChannelInterceptor {
    private final RealtimeAuthorization authorization;
    private final RealtimeSessionRegistry sessions;
    public RealtimeInboundInterceptor(RealtimeAuthorization authorization,RealtimeSessionRegistry sessions) {
        this.authorization=authorization; this.sessions=sessions;
    }
    @Override public Message<?> preSend(Message<?> message,MessageChannel channel) {
        var headers=MessageHeaderAccessor.getAccessor(message,StompHeaderAccessor.class);
        if (headers == null) throw new ForbiddenException("Realtime message denied");
        var command=headers.getCommand();
        if (command == null) return message; // transport heartbeat carries no business data
        if (StompCommand.DISCONNECT.equals(command)) return message; // includes server-generated transport cleanup
        String sessionId=headers.getSessionId();
        try {
            if (StompCommand.CONNECT.equals(command)) {
                List<String> values=headers.getNativeHeader("Authorization");
                if (values == null || values.size()!=1) throw new ForbiddenException("Realtime authentication required");
                String bearer=values.get(0);
                var user=authorization.identity(bearer);
                headers.setUser(sessions.authenticate(sessionId,user.userId(),bearer));
                headers.removeNativeHeader("Authorization");
                return message;
            }
            var identity=sessions.identity(sessionId);
            if (identity == null) throw new ForbiddenException("Realtime authentication required");
            authorization.identity(identity.bearer());
            if (StompCommand.SUBSCRIBE.equals(command)) authorization.require(identity.bearer(),headers.getDestination());
            else if (!StompCommand.UNSUBSCRIBE.equals(command) && !StompCommand.DISCONNECT.equals(command))
                throw new ForbiddenException("Realtime client command denied");
            return message;
        } catch (RuntimeException ex) {
            sessions.revoke(sessionId);
            throw new ForbiddenException("Realtime access denied");
        }
    }
}
