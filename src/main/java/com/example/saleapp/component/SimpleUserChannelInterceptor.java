package com.example.saleapp.component;

import com.example.saleapp.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.time.Instant;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class SimpleUserChannelInterceptor implements ChannelInterceptor {
    private static final Pattern SUBSCRIPTION = Pattern.compile("/topic/room\\.([1-9][0-9]*)");
    private static final Pattern MESSAGE = Pattern.compile("/app/chat\\.send\\.([1-9][0-9]*)");

    private final ChatService chats;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        var headers = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (headers == null) return message;
        var command = headers.getCommand();
        if (command == StompCommand.CONNECT || command == StompCommand.STOMP) {
            authorizedUser(headers.getUser());
        } else if (command == StompCommand.SUBSCRIBE || command == StompCommand.SEND) {
            String destination = headers.getDestination();
            Pattern allowed = command == StompCommand.SUBSCRIBE ? SUBSCRIPTION : MESSAGE;
            var match = allowed.matcher(destination == null ? "" : destination);
            if (!match.matches()) {
                throw new AccessDeniedException("허용되지 않은 채팅 경로입니다.");
            }
            try {
                chats.requireParticipant(Long.parseLong(match.group(1)), authorizedUser(headers.getUser()));
            } catch (NumberFormatException exception) {
                throw new AccessDeniedException("채팅방에 접근할 수 없습니다.", exception);
            }
        }
        return message;
    }
    private long authorizedUser(Principal principal) {
        long userId = ChatService.authenticatedUserId(principal);
        if (!(principal instanceof Authentication auth) || !(auth.getDetails() instanceof Instant expiry)
                || !expiry.isAfter(Instant.now())) {
            throw new AccessDeniedException("채팅 인증이 만료되었습니다.");
        }
        return userId;
    }
}
