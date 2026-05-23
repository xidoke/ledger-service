package com.xidoke.ledger.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.xidoke.ledger.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifies the generated OpenAPI 3 spec (LDG-70): the app boots with SpringDoc and {@code /v3/api-docs.yaml} covers the
 * domain endpoints, the {@code Idempotency-Key} header, and the RFC 7807 error shapes. Run with {@code -Dopenapi.dump}
 * to (re)write the committed {@code docs/api/openapi.yaml}: {@code ./mvnw test -Dtest=OpenApiDocsTest -Dopenapi.dump}.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocsTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void specCoversEndpointsHeaderAndErrorShapes() throws Exception {
        String yaml = mockMvc.perform(get("/v3/api-docs.yaml"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(yaml)
                .contains("/accounts")
                .contains("/accounts/{id}/topups")
                .contains("/transfers")
                .contains("Idempotency-Key")
                .contains("ProblemDetail")
                .contains("application/problem+json");

        if (Boolean.getBoolean("openapi.dump")) {
            Files.writeString(Path.of("docs/api/openapi.yaml"), yaml);
        }
    }
}
