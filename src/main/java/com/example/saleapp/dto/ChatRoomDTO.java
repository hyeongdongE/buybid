package com.example.saleapp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRoomDTO {
    private Long roomId;
    private Long userId;
    private String productTitle;
    private String partnerName;
    private String url;
    private Integer price;
}