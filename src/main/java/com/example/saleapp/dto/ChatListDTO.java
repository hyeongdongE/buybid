package com.example.saleapp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatListDTO {
    private String partnerName;
    private String timeAgo;
    private String message;
    private Long roomId;
    private String url;
}