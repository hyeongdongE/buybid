package com.example.saleapp.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "chatroom", uniqueConstraints = @UniqueConstraint(name = "uk_chatroom_product", columnNames = "product_id"))
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
    @JoinColumn(name = "product_id")
    private ProductsEntity productId;
}
