package com.yongyong.owlly.domain.chat.service;

import java.text.Normalizer;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** 채팅은 계좌 카드와 달리 원문의 일부도 보이지 않도록 가린다. */
@Component
public class ChatTextMasker {
    private static final String HIDDEN = "가려진 번호";
    private static final Pattern URL = Pattern.compile("(?iu)(?:(?:https?|ftp)://|www\\.)\\S+|(?<![\\p{L}\\p{N}_])(?:[\\p{L}\\p{N}](?:[\\p{L}\\p{N}-]*[\\p{L}\\p{N}])?\\.)+(?:com|net|org|kr|io|me|app|co|dev|xyz|edu|ac|[a-z]{2,})(?:[/?:#]\\S*)?");
    // 슬래시·밑줄로 나눈 전화번호·계좌번호도 하나의 숫자열로 인식한다.
    private static final Pattern NUMBER = Pattern.compile("(?<!\\p{Nd})(?:\\p{Nd}[\\p{Z}\\p{Pd}\\s().+/_]*){7,}\\p{Nd}(?!\\p{Nd})");

    public String mask(String text) {
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC)
                .replaceAll("[\\p{Cf}]", "").strip();
        return NUMBER.matcher(URL.matcher(normalized).replaceAll(HIDDEN)).replaceAll(HIDDEN).strip();
    }
}
