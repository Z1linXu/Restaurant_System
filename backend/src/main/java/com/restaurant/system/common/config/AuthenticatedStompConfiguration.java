package com.restaurant.system.common.config;

import com.restaurant.system.auth.service.TokenService;
import com.restaurant.system.common.auth.RoleCapabilityRegistry;
import com.restaurant.system.common.auth.StoreAccessService;
import com.restaurant.system.modules.StoreModuleAccessEvaluator;
import com.restaurant.system.user.repository.RoleRepository;
import com.restaurant.system.user.repository.StoreRepository;
import com.restaurant.system.user.repository.UserRepository;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@Profile("allo-test")
@ConditionalOnProperty(name = "app.realtime.authenticated-stomp-enabled", havingValue = "true")
public class AuthenticatedStompConfiguration implements WebSocketMessageBrokerConfigurer {
    private final AuthenticatedStompInterceptor interceptor;

    public AuthenticatedStompConfiguration(TokenService tokens, UserRepository users,
        RoleRepository roles, StoreAccessService stores, StoreRepository storeRepository, RoleCapabilityRegistry capabilities,
        StoreModuleAccessEvaluator modules) {
        interceptor = new AuthenticatedStompInterceptor(tokens, users, roles, stores, storeRepository,
            capabilities, modules, Clock.systemUTC());
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(interceptor);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override public Message<?> preSend(Message<?> message, MessageChannel channel) {
                return interceptor.filterOutbound(message);
            }
        });
    }
}
