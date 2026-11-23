package com.example.saleapp.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatDTO {
    private Long roomId;
    private Long chatId;
    private Long senderId;
    private String content;
    private LocalDateTime createdAt;
}
