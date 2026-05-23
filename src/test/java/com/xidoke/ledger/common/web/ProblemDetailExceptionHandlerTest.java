package com.xidoke.ledger.common.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.xidoke.ledger.common.security.SecurityConfig;
import com.xidoke.ledger.idempotency.adapter.in.IdempotencyFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

// Exclude IdempotencyFilter from this slice: @WebMvcTest auto-scans Filter beans, but that filter needs an
// IdempotencyStore (a JdbcClient @Repository) which a web slice doesn't provide. This test only exercises the advice.
@WebMvcTest(
        controllers = ProblemDetailExceptionHandlerTest.SampleController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = IdempotencyFilter.class))
@Import({
    ProblemDetailExceptionHandlerTest.SampleController.class,
    ProblemDetailExceptionHandler.class,
    SecurityConfig.class
})
class ProblemDetailExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unknownPathReturnsProblemDetail404() throws Exception {
        mockMvc.perform(get("/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Not Found"));
    }

    @Test
    void invalidBodyReturnsProblemDetail400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/sample/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    void illegalStateReturnsProblemDetail409() throws Exception {
        mockMvc.perform(get("/sample/conflict"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void unexpectedErrorReturnsProblemDetail500() throws Exception {
        mockMvc.perform(get("/sample/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
    }

    @RestController
    static class SampleController {

        @PostMapping("/sample/validate")
        String validate(@Valid @RequestBody SampleRequest request) {
            return request.name();
        }

        @org.springframework.web.bind.annotation.GetMapping("/sample/conflict")
        String conflict() {
            throw new IllegalStateException("sample conflict");
        }

        @org.springframework.web.bind.annotation.GetMapping("/sample/boom")
        String boom() {
            throw new RuntimeException("sample failure");
        }
    }

    record SampleRequest(@NotBlank String name) {}
}
