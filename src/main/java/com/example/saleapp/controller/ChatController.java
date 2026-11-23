package com.example.saleapp.controller;

import com.example.saleapp.config.CustomUserDetail;
import com.example.saleapp.dto.ChatRoomDTO;
import com.example.saleapp.dto.ChatListDTO;
import com.example.saleapp.dto.RowDTO;
import com.example.saleapp.entity.ChatMessageEntity;
import com.example.saleapp.entity.ChatRoomEntity;
import com.example.saleapp.entity.ProductsEntity;
import com.example.saleapp.entity.UsersEntity;
import com.example.saleapp.repository.*;
import com.example.saleapp.service.ChatService;
import com.example.saleapp.util.TimeAgoUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatController {
    private final ChatService chatService;
    private final ProductImageRepository productImageRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;

    @GetMapping("/chat/{id}")
    public String chat(@PathVariable("id") Long roomId, Model model, @AuthenticationPrincipal CustomUserDetail user) {
        ChatRoomEntity room = chatService.requireParticipant(roomId, user.getUser().getUserIdx());
        ProductsEntity product = room.getProductId();

        UsersEntity partner = (room.getBuyerId().getUserIdx().equals(user.getUser().getUserIdx())) ? room.getSellarId() : room.getBuyerId();
        String url = productImageRepository.findByProductId_ProductIdAndSort(product.getProductId(), 1).map(com.example.saleapp.entity.ProductImageEntity::getUrl).orElse("/image/default_product.png");
        ChatRoomDTO dto = ChatRoomDTO.builder()
                .partnerName(partner.getName())
                .roomId(roomId)
                .userId(user.getUser().getUserIdx())
                .productTitle(product.getTitle())
                .price(product.getCurrentPrice())
                .url(url)
                .build();
        model.addAttribute("dto", dto);
        return "chat";
    }

    // 채팅방 리스트
    @GetMapping("/chatlist")
    public String  chatlist(@AuthenticationPrincipal CustomUserDetail user, Model model) {
        Long myId = user.getUser().getUserIdx();
        List<ChatRoomEntity> rooms = chatRoomRepository.findByBuyerId_UserIdxOrSellarId_UserIdx(myId, myId);
        List<Long> roomIds = rooms.stream().map(ChatRoomEntity::getChatRoomId).toList();
        List<Long> productIds = rooms.stream().map(ChatRoomEntity::getProductId).filter(Objects::nonNull)
                .map(ProductsEntity::getProductId).distinct().toList();
        var latestByRoom = roomIds.isEmpty() ? Map.<Long, ChatMessageEntity>of() :
                chatMessageRepository.findLatestForRooms(roomIds).stream()
                        .collect(Collectors.toMap(message -> message.getRoomId().getChatRoomId(), Function.identity()));
        var imagesByProduct = productIds.isEmpty() ? Map.<Long, String>of() :
                productImageRepository.findMainForProductIds(productIds).stream()
                        .collect(Collectors.toMap(RowDTO::getProductId, RowDTO::getUrl));
        List<ChatListDTO> chatlist = rooms.stream().map(room -> {
            UsersEntity users = room.getBuyerId().getUserIdx().equals(myId) ?
                    room.getSellarId() : room.getBuyerId();
            String partnerName = users.getName();

            Optional<ChatMessageEntity> message = Optional.ofNullable(latestByRoom.get(room.getChatRoomId()));

            String lastMessage = message.map(ChatMessageEntity::getContent)
                    .orElse("대화를 시작해보세요!");

            String timeAgo = TimeAgoUtil.toTimeAgo(message.map(ChatMessageEntity::getCreatedAt).orElse(LocalDateTime.now()));

            String url = room.getProductId() == null ? "/image/default_product.png"
                    : imagesByProduct.getOrDefault(room.getProductId().getProductId(), "/image/default_product.png");

            Long roomId = room.getChatRoomId();
            return ChatListDTO.builder()
                    .message(lastMessage)
                    .partnerName(partnerName)
                    .timeAgo(timeAgo)
                    .roomId(roomId)
                    .url(url)
                    .build();
        }).toList();
        model.addAttribute("dto", chatlist);
        return "chatList";
    }
}
