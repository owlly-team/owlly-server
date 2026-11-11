package com.yongyong.owlly.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.io.IOException;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** S-08: 이름·학교·성별은 변경하지 않는다. 생략은 유지, 명시적인 null은 거부한다. */
@Getter
@NoArgsConstructor
public class StudentProfilePatch {
    @JsonSetter(nulls = Nulls.FAIL)
    @JsonDeserialize(using = StrictBoolean.class)
    private Boolean sameGenderDefault;

    @Valid @JsonSetter(nulls = Nulls.FAIL)
    private Point defaultDropoff;

    public boolean hasChanges() { return sameGenderDefault != null || defaultDropoff != null; }

    public record Point(
            @NotNull @DecimalMin("-90") @DecimalMax("90")
            @JsonDeserialize(using = StrictNumber.class) Double latitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180")
            @JsonDeserialize(using = StrictNumber.class) Double longitude
    ) {}

    public static class StrictBoolean extends StdDeserializer<Boolean> {
        public StrictBoolean() { super(Boolean.class); }
        @Override public Boolean deserialize(JsonParser p, DeserializationContext context) throws IOException {
            if (!p.hasToken(JsonToken.VALUE_TRUE) && !p.hasToken(JsonToken.VALUE_FALSE)) {
                throw MismatchedInputException.from(p, Boolean.class, "boolean 값이어야 합니다.");
            }
            return p.getBooleanValue();
        }
    }

    public static class StrictNumber extends StdDeserializer<Double> {
        public StrictNumber() { super(Double.class); }
        @Override public Double deserialize(JsonParser p, DeserializationContext context) throws IOException {
            if (!p.hasToken(JsonToken.VALUE_NUMBER_INT) && !p.hasToken(JsonToken.VALUE_NUMBER_FLOAT)) {
                throw MismatchedInputException.from(p, Double.class, "숫자여야 합니다.");
            }
            double value = p.getDoubleValue();
            if (!Double.isFinite(value)) throw MismatchedInputException.from(p, Double.class, "유한한 숫자여야 합니다.");
            return value;
        }
    }
}
