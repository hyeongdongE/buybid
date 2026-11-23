package com.example.saleapp.component;

import com.example.saleapp.config.CustomUserDetail;
import com.example.saleapp.entity.UsersEntity;
import com.example.saleapp.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatChannelTest {
    private final ChatService chats = mock(ChatService.class);
    private final SimpleUserChannelInterceptor interceptor = new SimpleUserChannelInterceptor(chats);

    @Test
    void checksParticipantForSubscriptionAndSend() {
        var participant = user(7, Instant.now().plusSeconds(60));
        var outsider = user(8, Instant.now().plusSeconds(60));
        doThrow(new AccessDeniedException("not a participant")).when(chats).requireParticipant(3, 8);
        assertThat(interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/topic/room.3", participant), null)).isNotNull();
        assertThat(interceptor.preSend(frame(StompCommand.SEND, "/app/chat.send.3", participant), null)).isNotNull();
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/topic/room.3", outsider), null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SEND, "/app/chat.send.3", outsider), null))
                .isInstanceOf(AccessDeniedException.class);
        verify(chats, times(2)).requireParticipant(3, 7);
    }

    @Test
    void restrictsConnectionAndBrokerDestinations() {
        var participant = user(7, Instant.now().plusSeconds(60));
        assertThat(interceptor.preSend(frame(StompCommand.CONNECT, null, participant), null)).isNotNull();
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT, null, null), null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SEND, "/topic/room.3", participant), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void rejectsActionsAfterAccessSessionExpires() {
        var expired = user(7, Instant.now().minusSeconds(1));
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/topic/room.3", expired), null))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(chats);
    }

    private Message<byte[]> frame(StompCommand command, String destination,
            UsernamePasswordAuthenticationToken principal) {
        var frame = StompHeaderAccessor.create(command);
        if (destination != null) frame.setDestination(destination);
        if (principal != null) frame.setUser(principal);
        frame.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], frame.getMessageHeaders());
    }

    private UsernamePasswordAuthenticationToken user(long id, Instant expiry) {
        var user = new CustomUserDetail(UsersEntity.builder().userIdx(id).email("user@example.test").role("ROLE_USER").build());
        var authentication = UsernamePasswordAuthenticationToken.authenticated(user, null, List.of());
        authentication.setDetails(expiry);
        return authentication;
    }
}
