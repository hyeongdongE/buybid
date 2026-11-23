package com.example.saleapp;

import com.example.saleapp.component.JwtTokenProvider;
import com.example.saleapp.dto.ChatDTO;
import com.example.saleapp.dto.ChatReqDTO;
import com.example.saleapp.entity.ChatRoomEntity;
import com.example.saleapp.entity.UsersEntity;
import com.example.saleapp.repository.ChatRoomRepository;
import com.example.saleapp.repository.UsersRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ChatSocketIntegrationTest {
    @LocalServerPort int port;
    @Autowired JwtTokenProvider tokens;
    @Autowired UsersRepository users;
    @Autowired ChatRoomRepository rooms;
    @Autowired ObjectMapper mapper;

    @Test
    void authenticatedParticipantReceivesSavedMessageAndLogoutClosesSocket() throws Exception {
        var buyer = account();
        var room = rooms.save(ChatRoomEntity.builder().buyerId(buyer).sellarId(account()).build());
        var closed = new CompletableFuture<Throwable>();
        var received = new CompletableFuture<ChatDTO>();
        var client = new WebSocketStompClient(new StandardWebSocketClient());
        var converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(mapper);
        client.setMessageConverter(converter);
        var headers = new WebSocketHttpHeaders();
        String accessCookie = "accessToken=" + tokens.createAccessToken(buyer.getEmail());
        headers.add("Cookie", accessCookie);
        try {
            var session = client.connectAsync("ws://127.0.0.1:" + port + "/ws/websocket", headers,
                    new StompSessionHandlerAdapter() {
                        @Override
                        public void handleTransportError(StompSession session, Throwable exception) {
                            closed.complete(exception);
                        }
                    }).get(8, TimeUnit.SECONDS);
            session.subscribe("/topic/room." + room.getChatRoomId(), new StompFrameHandler() {
                @Override public Type getPayloadType(StompHeaders headers) { return ChatDTO.class; }
                @Override public void handleFrame(StompHeaders headers, Object payload) {
                    received.complete((ChatDTO) payload);
                }
            });
            session.send("/app/chat.send." + room.getChatRoomId(), new ChatReqDTO("fixture message"));
            assertThat(received.get(8, TimeUnit.SECONDS).getContent()).isEqualTo("fixture message");
            logoutWithOnlyAccessCookie(accessCookie);
            assertThat(closed.get(8, TimeUnit.SECONDS)).isNotNull();
            assertThat(session.isConnected()).isFalse();
        } finally {
            client.stop();
        }
    }

    private void logoutWithOnlyAccessCookie(String accessCookie) throws Exception {
        var http = java.net.http.HttpClient.newHttpClient();
        var origin = "http://127.0.0.1:" + port;
        var signIn = http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(origin + "/signin")).GET().build(),
                java.net.http.HttpResponse.BodyHandlers.ofString());
        var csrf = java.util.regex.Pattern.compile("name=\"csrf-token\" content=\"([^\"]+)\"").matcher(signIn.body());
        assertThat(csrf.find()).isTrue();
        String csrfCookie = signIn.headers().allValues("set-cookie").stream()
                .filter(cookie -> cookie.startsWith("XSRF-TOKEN=")).findFirst().orElseThrow().split(";", 2)[0];
        var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(origin + "/api/logout"))
                .header("Cookie", accessCookie + "; " + csrfCookie).header("X-XSRF-TOKEN", csrf.group(1))
                .POST(java.net.http.HttpRequest.BodyPublishers.noBody()).build();
        assertThat(http.send(request, java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(204);
    }

    private UsersEntity account() {
        var name = UUID.randomUUID().toString();
        return users.save(UsersEntity.builder().name(name).email(name + "@example.test")
                .password("unused").role("ROLE_USER").created_at(LocalDateTime.now()).build());
    }
}
