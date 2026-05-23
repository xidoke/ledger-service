package com.xidoke.ledger.common.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * OpenAPI 3 metadata for the generated spec (LDG-70). Two things the controller signatures cannot express on their own
 * are added here: the {@code Idempotency-Key} header (enforced by a servlet filter, not a controller parameter — see
 * ADR-0012) and the RFC 7807 {@code problem+json} error responses (mapped by {@link ProblemDetailExceptionHandler} and
 * the idempotency filter, not declared per method). The money endpoints are matched by controller class name so this
 * shared-kernel config does not import the feature packages.
 */
@Configuration
public class OpenApiConfig {

    private static final String PROBLEM_SCHEMA = "ProblemDetail";
    private static final String PROBLEM_REF = "#/components/schemas/" + PROBLEM_SCHEMA;
    private static final String PROBLEM_JSON = "application/problem+json";

    @Bean
    OpenAPI ledgerOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Ledger Service API")
                        .version("0.1")
                        .description("Double-entry e-wallet ledger — accounts, top-ups, transfers. Money endpoints are "
                                + "exactly-once via the Idempotency-Key header (ADR-0012) and return RFC 7807 "
                                + "problem+json on error."))
                .components(new Components().addSchemas(PROBLEM_SCHEMA, problemDetailSchema()));
    }

    @Bean
    OperationCustomizer idempotencyAndProblemResponses() {
        return (operation, handlerMethod) -> {
            String controller = handlerMethod.getBeanType().getSimpleName();
            boolean money = controller.equals("TopupController") || controller.equals("TransferController");
            if (money) {
                operation.addParametersItem(idempotencyKeyHeader());
                addProblem(operation, "400", "Missing/invalid Idempotency-Key or malformed request body");
                addProblem(operation, "409", "Concurrency conflict, or an in-flight duplicate of this Idempotency-Key");
                addProblem(
                        operation,
                        "422",
                        "Business rule violation (e.g. insufficient funds), or Idempotency-Key reused with a "
                                + "different body");
            } else if (handlerMethod.hasMethodAnnotation(PostMapping.class)) {
                addProblem(operation, "400", "Malformed or invalid request body");
            }
            return operation;
        };
    }

    private static Parameter idempotencyKeyHeader() {
        return new Parameter()
                .in("header")
                .name("Idempotency-Key")
                .required(true)
                .description("Client-generated unique key making the operation exactly-once under retries (ADR-0012).")
                .schema(new StringSchema());
    }

    private static void addProblem(Operation operation, String code, String description) {
        ApiResponse response = new ApiResponse()
                .description(description)
                .content(new Content()
                        .addMediaType(PROBLEM_JSON, new MediaType().schema(new Schema<>().$ref(PROBLEM_REF))));
        operation.getResponses().addApiResponse(code, response);
    }

    private static Schema<?> problemDetailSchema() {
        return new ObjectSchema()
                .description("RFC 7807 problem detail")
                .addProperty("type", new StringSchema().format("uri"))
                .addProperty("title", new StringSchema())
                .addProperty("status", new IntegerSchema())
                .addProperty("detail", new StringSchema())
                .addProperty("instance", new StringSchema().format("uri"));
    }
}
