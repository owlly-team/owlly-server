package com.yongyong.owlly.domain.chat.repository;

import com.yongyong.owlly.domain.chat.entity.ChatSendReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatSendReceiptRepository extends JpaRepository<ChatSendReceipt, String> {}
