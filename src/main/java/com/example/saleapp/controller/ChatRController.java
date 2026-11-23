package com.example.saleapp.controller;

import com.example.saleapp.config.CustomUserDetail;
import com.example.saleapp.dto.ChatDTO;
import com.example.saleapp.dto.ChatHistory;
import com.example.saleapp.dto.ChatReqDTO;
import com.example.saleapp.repository.ChatMessageRepository;
import com.example.saleapp.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class ChatRController {
    private final SimpMessagingTemplate template;
    private final ChatService chatService;
    private final ChatMessageRepository chatMessages;

    @MessageMapping("/chat.send.{roomId}")
    public void send(@DestinationVariable Long roomId, ChatReqDTO dto, Principal principal) {
        ChatDTO message = chatService.saveChat(roomId, ChatService.authenticatedUserId(principal), dto);
        template.convertAndSend("/topic/room." + roomId, message);
    }

    @GetMapping("/chat/history/{roomId}")
    public ChatHistory history(@PathVariable Long roomId, @RequestParam(required = false) Long before,
            @AuthenticationPrincipal CustomUserDetail user) {
        chatService.requireParticipant(roomId, user.getUser().getUserIdx());
        int pageSize = 50;
        var rows = chatMessages.findHistory(roomId, before, PageRequest.of(0, pageSize + 1));
        Long next = rows.size() > pageSize ? rows.get(pageSize - 1).getChatId() : null;
        List<ChatDTO> messages = rows.stream().limit(pageSize).map(message -> ChatDTO.builder()
                .chatId(message.getChatId()).roomId(roomId).senderId(message.getSenderId().getUserIdx())
                .content(message.getContent()).createdAt(message.getCreatedAt()).build()).toList().reversed();
        return new ChatHistory(messages, next);
    }
}
