package com.example.saleapp.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "chatroom")
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatRoomEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chatRoomId")
    private Long chatRoomId;

    @ManyToOne
    @JoinColumn(name = "buyerId")
    private UsersEntity buyerId;

    @ManyToOne
    @JoinColumn(name = "sellarId")
    private UsersEntity sellarId;

    @ManyToOne
    @JoinColumn(name = "productId")
    private ProductsEntity productId;
}
