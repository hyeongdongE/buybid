package com.example.saleapp.repository;

import com.example.saleapp.entity.ChatMessageEntity;
import com.example.saleapp.entity.ChatRoomEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, Long> {
    Optional<ChatMessageEntity> findTopByRoomIdOrderByCreatedAtDesc(ChatRoomEntity room);
    List<ChatMessageEntity> findByRoomId_ChatRoomIdOrderByCreatedAtAsc(Long roomId);
}