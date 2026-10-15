package com.restaurant.system.common.config;

import com.restaurant.system.common.realtime.*;
import java.net.URI;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@Profile("!staging-synthetic-bootstrap")
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final RealtimeInboundInterceptor inbound;
    private final RealtimeOutboundInterceptor outbound;
    private final RealtimeSessionRegistry sessions;
    private final String[] origins;
    public WebSocketConfig(RealtimeInboundInterceptor inbound,RealtimeOutboundInterceptor outbound,
        RealtimeSessionRegistry sessions,@Value("${app.realtime.allowed-origins:https://restaurant-pad.local}") String origins) {
        this.inbound=inbound; this.outbound=outbound; this.sessions=sessions;
        this.origins=Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new);
        for (String origin : this.origins) {
            URI uri=URI.create(origin);
            if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) || uri.getHost()==null
                || uri.getRawUserInfo()!=null || uri.getRawQuery()!=null || uri.getRawFragment()!=null
                || (uri.getRawPath()!=null && !uri.getRawPath().isEmpty()))
                throw new IllegalArgumentException("Realtime origins must be exact HTTP origins");
        }
    }
    @Override public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic"); registry.setApplicationDestinationPrefixes("/app");
    }
    @Override public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOrigins(origins);
        registry.addEndpoint("/ws").setAllowedOrigins(origins).withSockJS();
    }
    @Override public void configureClientInboundChannel(ChannelRegistration registration) { registration.interceptors(inbound); }
    @Override public void configureClientOutboundChannel(ChannelRegistration registration) { registration.interceptors(outbound); }
    @Override public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.addDecoratorFactory(handler -> new WebSocketHandlerDecorator(handler) {
            @Override public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                sessions.opened(session); super.afterConnectionEstablished(session);
            }
            @Override public void afterConnectionClosed(WebSocketSession session,CloseStatus status) throws Exception {
                sessions.removed(session.getId()); super.afterConnectionClosed(session,status);
            }
        });
    }
}
