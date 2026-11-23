package com.example.saleapp.repository;

import com.example.saleapp.entity.ChatMessageEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, Long> {
    @Query("""
            select m from ChatMessageEntity m
            where m.roomId.chatRoomId = :roomId and (:before is null or m.chatId < :before)
            order by m.chatId desc
            """)
    List<ChatMessageEntity> findHistory(long roomId, Long before, Pageable limit);

    @Query("""
            select m from ChatMessageEntity m where m.roomId.chatRoomId in :roomIds
            and m.chatId = (select max(last.chatId) from ChatMessageEntity last where last.roomId = m.roomId)
            """)
    List<ChatMessageEntity> findLatestForRooms(List<Long> roomIds);
}
