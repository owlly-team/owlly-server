package com.yongyong.owlly.domain.auth.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.util.Locale;

public record StaffLoginRequest(
        @NotBlank @Email @Size(max = 255)
        @JsonDeserialize(using = CredentialStringDeserializer.class) String email,
        @NotEmpty @Size(max = 72)
        @JsonDeserialize(using = CredentialStringDeserializer.class) String password
) {
    public StaffLoginRequest {
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
        // 비밀번호는 정규화하지 않는다. UTF-8 바이트 길이는 서비스에서도 검사한다.
    }

    public static class CredentialStringDeserializer extends StdDeserializer<String> {
        public CredentialStringDeserializer() { super(String.class); }

        @Override
        public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (!parser.hasToken(JsonToken.VALUE_STRING)) {
                throw MismatchedInputException.from(parser, String.class, "인증 정보는 JSON 문자열이어야 합니다.");
            }
            return parser.getText();
        }
    }
}
