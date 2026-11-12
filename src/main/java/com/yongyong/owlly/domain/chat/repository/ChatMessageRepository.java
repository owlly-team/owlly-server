package com.yongyong.owlly.domain.chat.repository;

import com.yongyong.owlly.domain.chat.entity.ChatMessage;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    Optional<ChatMessage> findByClientMessageId(UUID clientMessageId);
    Optional<ChatMessage> findBySystemEventKey(String systemEventKey);
    List<ChatMessage> findByRoomIdOrderByIdAsc(Long roomId);
    List<ChatMessage> findByRoomIdOrderByIdDesc(Long roomId, Pageable page);
    List<ChatMessage> findByRoomIdAndIdLessThanOrderByIdDesc(Long roomId, Long beforeId, Pageable page);
    List<ChatMessage> findByRoomIdAndIdGreaterThanOrderByIdAsc(Long roomId, Long afterId, Pageable page);
}
