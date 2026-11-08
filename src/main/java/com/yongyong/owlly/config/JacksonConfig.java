package com.yongyong.owlly.config;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** JSON 직렬화 규칙 (docs/api.md 1절 "공통 계약"). */
@Configuration
public class JacksonConfig {

    /**
     * - bigint ID 는 문자열로 내려준다. 예: "401"
     *   (금액·횟수는 int 이므로 영향 없음. 응답 DTO 에 Long 을 쓰면 문자열이 되니 ID 에만 쓴다.)
     * - 요청에 정의에 없는 필드가 있으면 거부한다. (HttpMessageNotReadableException -> 422)
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer owllyJacksonCustomizer() {
        return builder -> builder
                .serializerByType(Long.class, ToStringSerializer.instance)
                .serializerByType(Long.TYPE, ToStringSerializer.instance)
                .failOnUnknownProperties(true);
    }
}
