package com.example.saleapp.service;

import com.example.saleapp.dto.ChatDTO;
import com.example.saleapp.dto.ChatReqDTO;
import com.example.saleapp.entity.ChatMessageEntity;
import com.example.saleapp.entity.ChatRoomEntity;
import com.example.saleapp.entity.UsersEntity;
import com.example.saleapp.repository.ChatMessageRepository;
import com.example.saleapp.repository.ChatRoomRepository;
import com.example.saleapp.repository.UsersRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ChatService {
    private final ChatRoomRepository chatRoomRepository;
    private final UsersRepository usersRepository;
    private final ChatMessageRepository chatMessageRepository;

    public ChatDTO saveChat(Long roomId, Long senderId, ChatReqDTO dto) {

        String content = dto.getContent();
        if (content.isEmpty()) throw new IllegalArgumentException("메시지 내용이 비어 있습니다.");
        if (content.length() > 1000) content = content.substring(0, 1000);

        ChatRoomEntity room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new AccessDeniedException("채팅방에 접근할 수 없습니다."));
        UsersEntity sender = usersRepository.findById(senderId)
                .orElseThrow(() -> new AccessDeniedException("사용자를 찾을 수 없습니다."));
        ChatMessageEntity chat = ChatMessageEntity.builder()
                .roomId(room)
                .senderId(sender)
                .content(dto.getContent())
                .createdAt(LocalDateTime.now())
                .build();
        ChatMessageEntity saved = chatMessageRepository.save(chat);
        return ChatDTO.builder()
                .senderId(saved.getSenderId().getUserIdx())
                .roomId(saved.getRoomId().getChatRoomId())
                .content(saved.getContent())
                .createdAt(saved.getCreatedAt())
                .build();
    }
}
