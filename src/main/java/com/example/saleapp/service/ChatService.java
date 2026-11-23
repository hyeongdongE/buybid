package com.example.saleapp.service;

import com.example.saleapp.config.CustomUserDetail;
import com.example.saleapp.dto.ChatDTO;
import com.example.saleapp.dto.ChatReqDTO;
import com.example.saleapp.entity.ChatMessageEntity;
import com.example.saleapp.entity.ChatRoomEntity;
import com.example.saleapp.repository.ChatMessageRepository;
import com.example.saleapp.repository.ChatRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class ChatService {
    private final ChatRoomRepository rooms;
    private final ChatMessageRepository messages;

    public ChatRoomEntity requireParticipant(long roomId, long userId) {
        ChatRoomEntity room = rooms.findById(roomId)
                .orElseThrow(() -> new AccessDeniedException("채팅방에 접근할 수 없습니다."));
        if (room.getBuyerId().getUserIdx() != userId && room.getSellarId().getUserIdx() != userId) {
            throw new AccessDeniedException("채팅방에 접근할 수 없습니다.");
        }
        return room;
    }

    public static long authenticatedUserId(Principal principal) {
        if (principal instanceof Authentication authentication && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof CustomUserDetail user) {
            return user.getUser().getUserIdx();
        }
        throw new AccessDeniedException("채팅에 로그인해야 합니다.");
    }

    public ChatDTO saveChat(long roomId, long senderId, ChatReqDTO dto) {
        ChatRoomEntity room = requireParticipant(roomId, senderId);
        String content = dto.getContent() == null ? "" : dto.getContent().strip();
        if (content.isEmpty() || content.length() > 1000) {
            throw new IllegalArgumentException("메시지는 1~1000자로 입력하세요.");
        }
        var sender = room.getBuyerId().getUserIdx() == senderId ? room.getBuyerId() : room.getSellarId();
        var saved = messages.save(ChatMessageEntity.builder().roomId(room).senderId(sender).content(content)
                .createdAt(LocalDateTime.now(ZoneId.of("Asia/Seoul"))).build());
        return ChatDTO.builder().chatId(saved.getChatId()).senderId(senderId).roomId(roomId)
                .content(saved.getContent()).createdAt(saved.getCreatedAt()).build();
    }
}
