package com.restaurant.system.common.realtime;

import java.io.IOException;
import java.security.Principal;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

@Component
public class RealtimeSessionRegistry {
    private final ConcurrentHashMap<String, Identity> identities=new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, WebSocketSession> transports=new ConcurrentHashMap<>();
    /** Never expose the credential in Principal names, logs or STOMP error frames. */
    public static final class Identity implements Principal {
        private final Long userId;
        private final String bearer;
        Identity(Long userId,String bearer) { this.userId=userId; this.bearer=bearer; }
        @Override public String getName() { return String.valueOf(userId); }
        public String bearer() { return bearer; }
        @Override public String toString() { return "RealtimeUser["+userId+"]"; }
    }
    public Identity authenticate(String sessionId,Long userId,String bearer) {
        if (sessionId == null || identities.containsKey(sessionId)) throw new com.restaurant.system.common.auth.ForbiddenException("Realtime session denied");
        Identity identity=new Identity(userId,bearer); identities.put(sessionId,identity); return identity;
    }
    public Identity identity(String sessionId) { return sessionId == null ? null : identities.get(sessionId); }
    public void opened(WebSocketSession session) { transports.put(session.getId(),session); }
    public void removed(String sessionId) { identities.remove(sessionId); transports.remove(sessionId); }
    public void revoke(String sessionId) {
        if (sessionId == null) return;
        identities.remove(sessionId);
        WebSocketSession session=transports.remove(sessionId);
        if (session != null && session.isOpen()) try { session.close(CloseStatus.POLICY_VIOLATION); } catch (IOException ignored) { }
    }
}
