package com.restaurant.system.common.realtime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.auth.service.impl.TokenServiceImpl;
import com.restaurant.system.common.auth.*;
import com.restaurant.system.common.config.WebSocketConfig;
import com.restaurant.system.modules.StoreModuleAccessEvaluator;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.*;

/** Real loopback HTTP/WebSocket/SockJS + Spring simple broker. No application DB or printer. */
@SpringBootTest(classes=WebSocketSecurityIntegrationTest.Application.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties={"spring.main.banner-mode=off","logging.level.root=WARN","logging.level.org.springframework.web.socket.messaging.StompSubProtocolHandler=OFF","app.realtime.allowed-origins=https://restaurant-pad.local"})
class WebSocketSecurityIntegrationTest {
    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude={DataSourceAutoConfiguration.class,HibernateJpaAutoConfiguration.class,FlywayAutoConfiguration.class})
    @Import({WebSocketConfig.class,RealtimeInboundInterceptor.class,RealtimeOutboundInterceptor.class,RealtimeSessionRegistry.class})
    static class Application {
        @Bean CommercialSecurityFixture fixture() { return new CommercialSecurityFixture(); }
        @Bean StoreModuleAccessEvaluator modules() { return mock(StoreModuleAccessEvaluator.class); }
        @Bean RealtimeAuthorization realtimeAuthorization(CommercialSecurityFixture f,StoreModuleAccessEvaluator modules) {
            return new RealtimeAuthorization(f.identities,f.access,f.capabilities,modules);
        }
    }
    @LocalServerPort int port;
    @Autowired CommercialSecurityFixture f;
    @Autowired StoreModuleAccessEvaluator modules;
    @Autowired SimpMessagingTemplate broker;
    @Autowired org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler simpleBroker;
    final List<WebSocketStompClient> clients=new ArrayList<>();
    @BeforeEach void restore() { reset(modules); f.member(4,10,"FRONTDESK",true);f.userRows.get(4L).setStatus("active"); }
    @AfterEach void close() { clients.forEach(WebSocketStompClient::stop);clients.clear(); }
    String topic(long store) { return "/topic/stores/"+store+"/frontdesk/orders"; }
    StompSession connect(String bearer,String origin,boolean sockjs) throws Exception { return connect(bearer,origin,sockjs,false); }
    StompSession connect(String bearer,String origin,boolean sockjs,boolean xhrOnly) throws Exception {
        WebSocketStompClient client=new WebSocketStompClient(sockjs
            ? new SockJsClient(xhrOnly ? List.of(new RestTemplateXhrTransport()) : List.of(new WebSocketTransport(new StandardWebSocketClient()),new RestTemplateXhrTransport()))
            : new StandardWebSocketClient());
        client.setMessageConverter(new StringMessageConverter());clients.add(client);
        WebSocketHttpHeaders http=new WebSocketHttpHeaders();http.setOrigin(origin);
        StompHeaders headers=new StompHeaders();if (bearer!=null) headers.add("Authorization",bearer);
        return client.connectAsync((sockjs ? "http" : "ws")+"://127.0.0.1:"+port+"/ws",http,headers,new StompSessionHandlerAdapter() {}).get(3,TimeUnit.SECONDS);
    }
    BlockingQueue<String> subscribe(StompSession session,String destination) throws Exception {
        BlockingQueue<String> messages=new LinkedBlockingQueue<>();
        StompHeaders subscriptionHeaders=new StompHeaders();subscriptionHeaders.setDestination(destination);subscriptionHeaders.setId(UUID.randomUUID().toString());
        session.subscribe(subscriptionHeaders,new StompFrameHandler() {
            public Type getPayloadType(StompHeaders headers) { return String.class; }
            public void handleFrame(StompHeaders headers,Object payload) { messages.add((String)payload); }
        });
        var headers=org.springframework.messaging.simp.SimpMessageHeaderAccessor.create(org.springframework.messaging.simp.SimpMessageType.MESSAGE);
        headers.setDestination(destination);
        var probe=org.springframework.messaging.support.MessageBuilder.createMessage(new byte[0],headers.getMessageHeaders());
        await(() -> simpleBroker.getSubscriptionRegistry().findSubscriptions(probe).values().stream()
            .anyMatch(ids -> ids.contains(subscriptionHeaders.getId())) || !session.isConnected());
        return messages;
    }
    void await(BooleanSupplier ready) throws Exception {
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
        while (!ready.getAsBoolean() && System.nanoTime()<end) Thread.sleep(20);
        assertThat(ready.getAsBoolean()).isTrue();
    }
    @Test void authenticatedOwnStoreReceivesAndReconnectsWithoutForeignEvents() throws Exception {
        StompSession a=connect(f.bearer(1),"http://127.0.0.1:"+port,false);
        StompSession b=connect(f.bearer(2),"https://restaurant-pad.local",false);
        var qa=subscribe(a,topic(10));var qb=subscribe(b,topic(20));
        broker.convertAndSend(topic(10),"org-a-refresh");
        assertThat(qa.poll(2,TimeUnit.SECONDS)).isEqualTo("org-a-refresh");
        assertThat(qb.poll(150,TimeUnit.MILLISECONDS)).isNull();
        a.disconnect();
        var reconnected=subscribe(connect(f.bearer(1),"https://restaurant-pad.local",true),topic(11));
        broker.convertAndSend(topic(11),"same-org-sibling");
        assertThat(reconnected.poll(2,TimeUnit.SECONDS)).isEqualTo("same-org-sibling");
    }
    @Test void anonymousInvalidAndExpiredConnectHaveNoBusinessSession() {
        assertThatThrownBy(() -> connect(null,"https://restaurant-pad.local",false)).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> connect("Bearer invalid","https://restaurant-pad.local",false)).isInstanceOf(Exception.class);
        var expired=new TokenServiceImpl(new ObjectMapper(),"synthetic-test-signing-key-never-a-runtime-secret",-1);
        assertThatThrownBy(() -> connect("Bearer "+expired.createAccessToken(f.actor(4),100L),"https://restaurant-pad.local",true)).isInstanceOf(Exception.class);
    }
    @Test void nativeHandshakeAndSockJsInfoRejectUnapprovedOrigins() throws Exception {
        assertThatThrownBy(() -> connect(f.bearer(4),"https://unrelated.invalid",false)).isInstanceOf(Exception.class);
        var response=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/ws/info"))
            .header("Origin","https://unrelated.invalid").GET().build(),HttpResponse.BodyHandlers.discarding());
        assertThat(response.statusCode()).isEqualTo(403);
    }
    @Test void foreignWildcardUnknownAndRoleInappropriateTopicsCloseSession() throws Exception {
        for (String destination : List.of(topic(20),"/topic/stores/*/frontdesk/orders","/topic/stores/10/**","/topic/stores/10/unknown","/topic/stores/10/kds/noodle-display")) {
            StompSession session=connect(f.bearer(4),"https://restaurant-pad.local",false);
            BlockingQueue<String> queue=new LinkedBlockingQueue<>();
            session.subscribe(destination,new StompFrameHandler() {
                public Type getPayloadType(StompHeaders h){return String.class;}
                public void handleFrame(StompHeaders h,Object p){queue.add((String)p);}
            });
            await(() -> !session.isConnected());broker.convertAndSend(topic(20),"foreign-secret");
            assertThat(queue.poll(50,TimeUnit.MILLISECONDS)).isNull();
        }
    }
    @Test void clientCannotForgeServerPublish() throws Exception {
        StompSession session=connect(f.bearer(4),"https://restaurant-pad.local",false);
        session.send(topic(10),"forged-refresh");await(() -> !session.isConnected());
    }
    @Test void existingSubscriptionIsClosedAndReceivesNothingAfterMembershipRevocation() throws Exception {
        StompSession session=connect(f.bearer(4),"https://restaurant-pad.local",true);
        var queue=subscribe(session,topic(10));
        f.member(4,10,"FRONTDESK",false);broker.convertAndSend(topic(10),"must-not-arrive");
        await(() -> !session.isConnected());assertThat(queue.poll(100,TimeUnit.MILLISECONDS)).isNull();
    }
    @Test void existingSubscriptionIsClosedAfterUserDeactivation() throws Exception {
        StompSession session=connect(f.bearer(4),"https://restaurant-pad.local",false);
        var queue=subscribe(session,topic(10));
        f.userRows.get(4L).setStatus("inactive");broker.convertAndSend(topic(10),"must-not-arrive");
        await(() -> !session.isConnected());assertThat(queue.poll(100,TimeUnit.MILLISECONDS)).isNull();
    }
    @Test void existingSubscriptionExpiresAndAdminCannotSubscribe() throws Exception {
        var shortLived=new TokenServiceImpl(new ObjectMapper(),"synthetic-test-signing-key-never-a-runtime-secret",2);
        StompSession session=connect("Bearer "+shortLived.createAccessToken(f.actor(4),100L),"https://restaurant-pad.local",false);
        var queue=subscribe(session,topic(10));Thread.sleep(2100);
        broker.convertAndSend(topic(10),"must-not-arrive");await(() -> !session.isConnected());
        assertThat(queue.poll(100,TimeUnit.MILLISECONDS)).isNull();
        StompSession admin=connect(f.bearer(5),"https://restaurant-pad.local",false);
        subscribe(admin,topic(10));await(() -> !admin.isConnected());
    }    @Test void sockJsXhrTransportAndKitchenRoleRemainCompatible() throws Exception {
        StompSession session=connect(f.bearer(10),"https://restaurant-pad.local",true,true);
        String hot="/topic/stores/10/kds/hot-kitchen";
        var queue=subscribe(session,hot);broker.convertAndSend(hot,"kitchen-refresh");
        assertThat(queue.poll(2,TimeUnit.SECONDS)).isEqualTo("kitchen-refresh");
    }
    @Test void disabledModuleStopsAnExistingSubscription() throws Exception {
        StompSession session=connect(f.bearer(4),"https://restaurant-pad.local",false);
        var queue=subscribe(session,topic(10));
        doThrow(new com.restaurant.system.common.exception.BusinessException("MODULE_DISABLED"))
            .when(modules).requireCapability(10L,com.restaurant.system.modules.ModuleKeys.ORDERING_POS);
        broker.convertAndSend(topic(10),"must-not-arrive");await(() -> !session.isConnected());
        assertThat(queue.poll(100,TimeUnit.MILLISECONDS)).isNull();
    }
    @Test void anonymousTransportCannotSubscribeBeforeConnectAuthentication() throws Exception {
        CountDownLatch closed=new CountDownLatch(1);
        List<String> frames=new CopyOnWriteArrayList<>();
        var socket=HttpClient.newHttpClient().newWebSocketBuilder().header("Origin","https://restaurant-pad.local")
            .buildAsync(URI.create("ws://127.0.0.1:"+port+"/ws"),new WebSocket.Listener() {
                public void onOpen(WebSocket socket) { socket.request(1); }
                public CompletionStage<?> onText(WebSocket socket,CharSequence data,boolean last) {
                    frames.add(data.toString());socket.request(1);return null;
                }
                public CompletionStage<?> onClose(WebSocket socket,int status,String reason) { closed.countDown();return null; }
            }).get(3,TimeUnit.SECONDS);
        socket.sendText("SUBSCRIBE\nid:anonymous\ndestination:"+topic(20)+"\n\n\u0000",true).join();
        assertThat(closed.await(3,TimeUnit.SECONDS)).isTrue();
        broker.convertAndSend(topic(20),"foreign-business-secret");
        assertThat(String.join("",frames)).doesNotContain("foreign-business-secret");
    }

}
