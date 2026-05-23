package com.xidoke.ledger.topup.adapter.in;

import com.xidoke.ledger.common.concurrency.OptimisticRetry;
import com.xidoke.ledger.common.domain.AccountId;
import com.xidoke.ledger.topup.application.TopupResult;
import com.xidoke.ledger.topup.application.TopupService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Inbound REST adapter for the top-up use case. */
@RestController
@RequestMapping("/accounts/{id}/topups")
public class TopupController {

    private final TopupService topupService;
    private final OptimisticRetry retry;

    public TopupController(TopupService topupService, OptimisticRetry retry) {
        this.topupService = topupService;
        this.retry = retry;
    }

    @PostMapping
    ResponseEntity<TopupResponse> topup(@PathVariable UUID id, @Valid @RequestBody TopupRequest request) {
        // SYSTEM_FUNDING is debited by every top-up, so concurrent top-ups contend on one row — retry on conflict
        // (ADR-0011); each attempt reloads at the current version. Exhaustion → 409.
        TopupResult result = retry.execute(() -> topupService.topup(AccountId.of(id), request.amountMinorUnits()));
        TopupResponse body = TopupResponse.from(result);
        return ResponseEntity.created(URI.create("/transactions/" + body.transactionId()))
                .body(body);
    }
}
