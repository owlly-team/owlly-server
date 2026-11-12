package com.yongyong.owlly.domain.chat.dto;

import java.util.List;

public record MessagePage(List<MessageResponse> data, MessagePageMeta meta) {
    public record MessagePageMeta(Long nextBeforeId, Long latestId, boolean hasMore) {}
}
