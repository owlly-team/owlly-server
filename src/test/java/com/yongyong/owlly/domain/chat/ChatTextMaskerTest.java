package com.yongyong.owlly.domain.chat;

import static org.assertj.core.api.Assertions.assertThat;
import com.yongyong.owlly.domain.chat.service.ChatTextMasker;
import org.junit.jupiter.api.Test;

class ChatTextMaskerTest {
    private final ChatTextMasker masker = new ChatTextMasker();

    @Test void masksPhoneAccountAndLinksIncludingCommonObfuscations() {
        for (String input : new String[]{"010-1234-5678", "010 1234 5678", "010.1234.5678", "010—1234—5678",
                "010/1234/5678", "010_1234_5678", "1234/5678/9012", "1234_5678_9012",
                "０１０／１２３４／５６７８", "０１０＿１２３４＿５６７８",
                "１１０－１２３－４５６７８９", "010\u200b1234\u200b5678", "https://example.com/a?q=b", "www.example.com", "example.co.kr/path"})
            assertThat(masker.mask(input)).isEqualTo("가려진 번호");
    }

    @Test void keepsMeetingCodeOrdinaryChatAndAmount() {
        assertThat(masker.mask("확인 코드 0042 맞아요. 9500원 보냈어요")).isEqualTo("확인 코드 0042 맞아요. 9500원 보냈어요");
        assertThat(masker.mask(" 정문 편의점 앞이에요 ")).isEqualTo("정문 편의점 앞이에요");
    }
}
