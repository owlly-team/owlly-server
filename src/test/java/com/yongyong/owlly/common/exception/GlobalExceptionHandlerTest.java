package com.yongyong.owlly.common.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yongyong.owlly.common.response.ApiResponse;
import com.yongyong.owlly.config.JacksonConfig;
import com.yongyong.owlly.config.SecurityConfig;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 공통 응답·오류 형식이 docs/api.md 1절·6절과 일치하는지 확인한다. */
@WebMvcTest(controllers = GlobalExceptionHandlerTest.TestController.class)
@Import({JacksonConfig.class, SecurityConfig.class, GlobalExceptionHandlerTest.TestController.class})
class GlobalExceptionHandlerTest {

    @Autowired
    MockMvc mockMvc;

    // ===== 성공 =====

    @Test
    void 성공_응답은_data로_감싸고_Long_ID는_문자열이다() throws Exception {
        mockMvc.perform(get("/test/ok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("401"))
                .andExpect(jsonPath("$.data.name").value("거점"))
                .andExpect(jsonPath("$.meta").doesNotExist());
    }

    // ===== 업무 예외 =====

    @Test
    void 업무_예외는_코드에_매핑된_상태와_error_형식으로_응답한다() throws Exception {
        mockMvc.perform(get("/test/owlly"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("JOURNEY_ALREADY_RESOLVED"))
                .andExpect(jsonPath("$.error.message").value("이미 결과가 확정된 귀가입니다."))
                .andExpect(jsonPath("$.error.details").isEmpty())
                .andExpect(jsonPath("$.error.traceId").isNotEmpty());
    }

    // ===== 요청 본문 =====

    @Test
    void 본문_검증_실패는_422_VALIDATION_ERROR와_필드별_details를_준다() throws Exception {
        mockMvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0]").value(Matchers.startsWith("name:")));
    }

    @Test
    void 정의에_없는_필드는_422_VALIDATION_ERROR이고_값은_노출하지_않는다() throws Exception {
        mockMvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"a\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0]").value("role: 정의되지 않은 필드입니다."));
    }

    @Test
    void 본문_필드_타입_오류는_422_VALIDATION_ERROR이다() throws Exception {
        mockMvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"a\",\"count\":\"abc\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0]").value("count: 형식이 올바르지 않습니다."));
    }

    @Test
    void 깨진_JSON은_400_INVALID_REQUEST이다() throws Exception {
        mockMvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    // ===== 쿼리 파라미터 =====

    @Test
    void 숫자_자리에_문자가_오면_422_VALIDATION_ERROR이다() throws Exception {
        mockMvc.perform(get("/test/param").param("id", "abc"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0]").value("id: 형식이 올바르지 않습니다."));
    }

    @Test
    void 파라미터_검증_실패는_422_VALIDATION_ERROR이다() throws Exception {
        mockMvc.perform(get("/test/param").param("id", "-1"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0]").value(Matchers.startsWith("id:")));
    }

    @Test
    void 필수_파라미터가_없으면_422_VALIDATION_ERROR이다() throws Exception {
        mockMvc.perform(get("/test/param"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0]").value("id: 필수 값입니다."));
    }

    // ===== 헤더 =====

    @Test
    void Idempotency_Key가_없으면_422_VALIDATION_ERROR이다() throws Exception {
        mockMvc.perform(post("/test/idempotent"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0]").value("Idempotency-Key: 필수 헤더입니다."));
    }

    @Test
    void If_Match가_없으면_428_PRECONDITION_REQUIRED이다() throws Exception {
        mockMvc.perform(post("/test/conditional"))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.error.code").value("PRECONDITION_REQUIRED"));
    }

    // ===== 권한 =====

    @Test
    void 메서드_보안의_권한_거부는_403_FORBIDDEN이다() throws Exception {
        mockMvc.perform(get("/test/denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    // ===== 스프링 기본 예외·예상 못 한 예외 =====

    @Test
    void 없는_경로는_404_RESOURCE_NOT_FOUND이다() throws Exception {
        mockMvc.perform(get("/test/no-such-path"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void 지원하지_않는_메서드는_405를_유지하고_INVALID_REQUEST이다() throws Exception {
        mockMvc.perform(delete("/test/ok"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    void 예상_못_한_예외는_500이고_내부_메시지를_노출하지_않는다() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.message").value("서버 오류가 발생했습니다."))
                .andExpect(jsonPath("$.error.traceId").isNotEmpty());
    }

    /** 테스트 전용 컨트롤러. */
    @RestController
    static class TestController {

        record Item(Long id, String name) {}

        record Body(@NotBlank String name, Integer count) {}

        @GetMapping("/test/ok")
        ApiResponse<Item> ok() {
            return ApiResponse.of(new Item(401L, "거점"));
        }

        @GetMapping("/test/owlly")
        void owlly() {
            throw new OwllyException(ErrorCode.JOURNEY_ALREADY_RESOLVED);
        }

        @PostMapping("/test/body")
        ApiResponse<Body> body(@Valid @RequestBody Body body) {
            return ApiResponse.of(body);
        }

        @GetMapping("/test/param")
        ApiResponse<Long> param(@RequestParam @Positive Long id) {
            return ApiResponse.of(id);
        }

        @PostMapping("/test/idempotent")
        void idempotent(@RequestHeader("Idempotency-Key") String key) {
        }

        @PostMapping("/test/conditional")
        void conditional(@RequestHeader("If-Match") String etag) {
        }

        @GetMapping("/test/denied")
        void denied() {
            throw new AccessDeniedException("denied");
        }

        @GetMapping("/test/boom")
        void boom() {
            throw new IllegalStateException("내부 상세: DB 비밀번호 등");
        }
    }
}
