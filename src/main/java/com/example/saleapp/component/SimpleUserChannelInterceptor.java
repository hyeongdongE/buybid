package com.example.saleapp.component;

import com.example.saleapp.repository.UsersRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
@Slf4j
@RequiredArgsConstructor
public class SimpleUserChannelInterceptor implements ChannelInterceptor {
    private final JwtTokenProvider jwtTokenProvider;
    private final UsersRepository usersRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor acc = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (acc == null) return message;

        if (StompCommand.CONNECT.equals(acc.getCommand())) {
            try {
                String header = acc.getFirstNativeHeader("Authorization");
                if (header == null || !header.startsWith("Bearer ")) {
                    throw new AccessDeniedException("인증 토큰이 포함되어 있지 않거나 형식이 올바르지 않습니다.");
                }

                String token = header.substring(7);
                if (!jwtTokenProvider.validateToken(token)) {
                    throw new AccessDeniedException("유효하지않은 토큰입니다.");
                }

                String email = jwtTokenProvider.getEmail(token);
                Long userId = usersRepository.findByEmail(email)
                        .orElseThrow(() -> new AccessDeniedException("유저 없음"))
                        .getUserIdx();

                acc.getSessionAttributes().put("AUTHENTICATED_USER_ID", userId);
                acc.setUser(new UsernamePasswordAuthenticationToken(String.valueOf(userId), null, Collections.emptyList()));
            } catch (AccessDeniedException e) {
                throw e;
            } catch (Exception e) {
                throw new AccessDeniedException("WebSocket 인증 과정에서 오류 발생", e);
            }
        }

        return message;
    }
}