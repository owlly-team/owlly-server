package com.yongyong.owlly.common.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yongyong.owlly.common.response.ApiResponse;
import com.yongyong.owlly.config.JacksonConfig;
import com.yongyong.owlly.config.SecurityConfig;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 공통 응답·오류 형식이 docs/api.md 1절과 일치하는지 확인한다. */
@WebMvcTest(controllers = GlobalExceptionHandlerTest.TestController.class)
@Import({JacksonConfig.class, SecurityConfig.class, GlobalExceptionHandlerTest.TestController.class})
class GlobalExceptionHandlerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void 성공_응답은_data로_감싸고_Long_ID는_문자열이다() throws Exception {
        mockMvc.perform(get("/test/ok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("401"))
                .andExpect(jsonPath("$.data.name").value("거점"))
                .andExpect(jsonPath("$.meta").doesNotExist());
    }

    @Test
    void 업무_예외는_코드에_매핑된_상태와_error_형식으로_응답한다() throws Exception {
        mockMvc.perform(get("/test/owlly"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("JOURNEY_ALREADY_RESOLVED"))
                .andExpect(jsonPath("$.error.message").value("이미 결과가 확정된 귀가입니다."))
                .andExpect(jsonPath("$.error.details").isEmpty())
                .andExpect(jsonPath("$.error.traceId").isNotEmpty());
    }

    @Test
    void 검증_실패는_422와_필드별_details를_준다() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.error.details[0]").value(org.hamcrest.Matchers.startsWith("name:")));
    }

    @Test
    void 정의에_없는_필드가_오면_422이다() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"a\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    void 깨진_JSON은_422이다() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isUnprocessableEntity())
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

    @Test
    void 없는_경로는_404_RESOURCE_NOT_FOUND이다() throws Exception {
        mockMvc.perform(get("/test/no-such-path"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    /** 테스트 전용 컨트롤러. */
    @RestController
    static class TestController {

        record Item(Long id, String name) {}

        record Body(@NotBlank String name) {}

        @GetMapping("/test/ok")
        ApiResponse<Item> ok() {
            return ApiResponse.of(new Item(401L, "거점"));
        }

        @GetMapping("/test/owlly")
        void owlly() {
            throw new OwllyException(ErrorCode.JOURNEY_ALREADY_RESOLVED);
        }

        @PostMapping("/test/validate")
        ApiResponse<Body> validate(@Valid @RequestBody Body body) {
            return ApiResponse.of(body);
        }

        @GetMapping("/test/boom")
        void boom() {
            throw new IllegalStateException("내부 상세: DB 비밀번호 등");
        }
    }
}
