package com.example.saleapp.config;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class ChatSocketSessions {
    private final Map<String, ActiveSocket> sockets = new ConcurrentHashMap<>();
    private final ScheduledThreadPoolExecutor expiry = new ScheduledThreadPoolExecutor(1,
            Thread.ofPlatform().daemon(true).name("chat-expiry").factory());

    public ChatSocketSessions() {
        expiry.setRemoveOnCancelPolicy(true);
    }

    public WebSocketHandler decorate(WebSocketHandler delegate) {
        return new WebSocketHandlerDecorator(delegate) {
            @Override
            public void afterConnectionEstablished(WebSocketSession socket) throws Exception {
                if (!(socket.getPrincipal() instanceof Authentication auth)
                        || !(auth.getDetails() instanceof Instant expires) || !expires.isAfter(Instant.now())) {
                    socket.close(CloseStatus.POLICY_VIOLATION);
                    return;
                }
                long delay = Math.max(1, Duration.between(Instant.now(), expires).toMillis());
                // Schedule after tracking so that immediate expiry can release the same entry.
                sockets.compute(socket.getId(), (id, previous) -> {
                    var task = expiry.schedule(() -> close(socket), delay, TimeUnit.MILLISECONDS);
                    return new ActiveSocket(auth.getName(), socket, task);
                });
                try {
                    super.afterConnectionEstablished(socket);
                } catch (Exception exception) {
                    untrack(socket.getId());
                    throw exception;
                }
            }

            @Override
            public void afterConnectionClosed(WebSocketSession socket, CloseStatus status) throws Exception {
                untrack(socket.getId());
                super.afterConnectionClosed(socket, status);
            }
        };
    }

    @EventListener
    public void onLogout(AccountLogout event) {
        sockets.values().stream().filter(socket -> socket.email().equals(event.email()))
                .forEach(socket -> close(socket.session()));
    }

    private void close(WebSocketSession socket) {
        untrack(socket.getId());
        try {
            if (socket.isOpen()) socket.close(CloseStatus.POLICY_VIOLATION);
        } catch (IOException exception) {
            log.debug("채팅 연결을 닫지 못했습니다. sessionId={}", socket.getId(), exception);
        }
    }

    private void untrack(String sessionId) {
        var active = sockets.remove(sessionId);
        if (active != null) active.expiryTask().cancel(false);
    }

    @PreDestroy
    void shutdown() {
        expiry.shutdownNow();
    }

    private record ActiveSocket(String email, WebSocketSession session, ScheduledFuture<?> expiryTask) {}

    public record AccountLogout(String email) {}
}
