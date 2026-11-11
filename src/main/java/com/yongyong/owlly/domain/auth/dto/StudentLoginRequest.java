package com.yongyong.owlly.domain.auth.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.IOException;

public record StudentLoginRequest(
        @NotBlank @Size(max = 512)
        @JsonDeserialize(using = VerificationTokenDeserializer.class) String verificationToken
) {
    /** 이 필드에서만 숫자·불리언의 문자열 자동 변환을 막는다. */
    public static class VerificationTokenDeserializer extends StdDeserializer<String> {
        public VerificationTokenDeserializer() {
            super(String.class);
        }

        @Override
        public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (!parser.hasToken(JsonToken.VALUE_STRING)) {
                throw MismatchedInputException.from(parser, String.class, "인증 증표는 JSON 문자열이어야 합니다.");
            }
            return parser.getText();
        }
    }
}
