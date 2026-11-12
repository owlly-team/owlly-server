package com.yongyong.owlly.domain.chat.service;

import com.yongyong.owlly.domain.chat.repository.ChatRoomRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "owlly.chat.scheduling-enabled", havingValue = "true", matchIfMissing = true)
public class ChatRoomScheduler {
    private final ChatRoomRepository rooms;
    private final ChatRoomService chat;

    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void synchronize() {
        long after = 0;
        while (true) {
            var ids = rooms.findOpenPartyIdsAfter(after, PageRequest.of(0, 100));
            if (ids.isEmpty()) return;
            for (Long id : ids) {
                try { chat.synchronizeRoom(id, Instant.now()); }
                catch (RuntimeException e) { log.warn("채팅방 동기화 실패 partyId={}", id, e); }
            }
            after = ids.getLast();
        }
    }
}
