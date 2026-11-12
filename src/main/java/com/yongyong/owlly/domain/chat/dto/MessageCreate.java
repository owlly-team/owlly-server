package com.yongyong.owlly.domain.chat.dto;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.validation.constraints.*;
import java.io.IOException;

public record MessageCreate(
        @NotBlank @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
        @JsonDeserialize(using = StrictString.class) String clientMessageId,
        @NotBlank @Size(max = 200) @JsonDeserialize(using = StrictString.class) String text) {
    public static class StrictString extends StdDeserializer<String> {
        public StrictString() { super(String.class); }
        @Override public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (!parser.hasToken(JsonToken.VALUE_STRING))
                throw MismatchedInputException.from(parser, String.class, "JSON 문자열이어야 합니다.");
            return parser.getText();
        }
    }
}
