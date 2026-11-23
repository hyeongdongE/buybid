package com.example.saleapp.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_message")
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chatId")
    private Long chatId;

    @Column(length = 1000)
    private String content;
    private LocalDateTime createdAt;

    @ManyToOne
    @JoinColumn(name = "senderId")
    private UsersEntity senderId;

    @ManyToOne
    @JoinColumn(name = "roomId")
    private ChatRoomEntity roomId;
}
