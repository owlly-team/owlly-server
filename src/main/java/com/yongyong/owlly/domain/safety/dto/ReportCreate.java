package com.yongyong.owlly.domain.safety.dto;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.validation.constraints.*;
import java.io.IOException;

public record ReportCreate(
        @NotNull @Pattern(regexp = "^[1-9][0-9]{0,18}$")
        @JsonDeserialize(using = StrictString.class) String targetMemberId,
        @NotNull @Pattern(regexp = "ABUSIVE_LANGUAGE|PRIVACY_REQUEST|NO_SHOW|IDENTITY_MISMATCH|OTHER")
        @JsonDeserialize(using = StrictString.class) String reasonCode,
        @Pattern(regexp = "^[1-9][0-9]{0,18}$")
        @JsonDeserialize(using = StrictString.class) String messageId,
        @Size(max = 200) @JsonDeserialize(using = StrictString.class) String details) {
    public static class StrictString extends StdDeserializer<String> {
        public StrictString() { super(String.class); }
        @Override public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (!parser.hasToken(JsonToken.VALUE_STRING))
                throw MismatchedInputException.from(parser, String.class, "JSON 문자열이어야 합니다.");
            return parser.getText();
        }
    }
}
