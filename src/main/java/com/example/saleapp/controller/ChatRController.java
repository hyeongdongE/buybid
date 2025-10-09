package com.example.saleapp.controller;

import com.example.saleapp.config.CustomUserDetail;
import com.example.saleapp.dto.ChatDTO;
import com.example.saleapp.dto.ChatReqDTO;
import com.example.saleapp.entity.ChatMessageEntity;
import com.example.saleapp.repository.ChatMessageRepository;
import com.example.saleapp.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ChatRController {
    private final SimpMessagingTemplate template;
    private final ChatService chatService;
    private final ChatMessageRepository chatMessageRepository;

    @MessageMapping("/chat.send.{roomId}")
    public void send(@DestinationVariable String roomId, ChatReqDTO dto, Principal principal) {
        Long senderId = Long.valueOf(principal.getName());
        Long rid = Long.parseLong(roomId);
        log.info("들어옴 : {}, {}",roomId, senderId);
        ChatDTO chatDto = chatService.saveChat(rid, senderId, dto);
        // 방 구독자들에게 방송: 클라 구독 경로는 '/topic/room.{roomId}'
        template.convertAndSend("/topic/room." + roomId, chatDto);
    }

    @GetMapping("/chat/history/{roomId}")
    public List<ChatDTO> history(@PathVariable("roomId") Long roomId) {
        List<ChatMessageEntity> list =
                chatMessageRepository.findByRoomId_ChatRoomIdOrderByCreatedAtAsc(roomId);
        return list.stream().map(message -> ChatDTO.builder()
                .senderId(message.getSenderId().getUserIdx())
                .content(message.getContent())
                .createdAt(message.getCreatedAt())
                .build()).toList();
    }
}
