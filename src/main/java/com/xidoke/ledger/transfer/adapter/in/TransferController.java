package com.xidoke.ledger.transfer.adapter.in;

import com.xidoke.ledger.common.concurrency.OptimisticRetry;
import com.xidoke.ledger.common.domain.AccountId;
import com.xidoke.ledger.transfer.application.TransferResult;
import com.xidoke.ledger.transfer.application.TransferService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Inbound REST adapter for the transfer use case. */
@RestController
@RequestMapping("/transfers")
public class TransferController {

    private final TransferService transferService;
    private final OptimisticRetry retry;

    public TransferController(TransferService transferService, OptimisticRetry retry) {
        this.transferService = transferService;
        this.retry = retry;
    }

    @PostMapping
    ResponseEntity<TransferResponse> transfer(@Valid @RequestBody TransferRequest request) {
        // Retry the whole posting on an optimistic-lock conflict: each attempt is a fresh @Transactional call that
        // reloads the accounts at their current version (ADR-0011). Exhaustion → 409.
        TransferResult result = retry.execute(() -> transferService.transfer(
                AccountId.of(request.fromAccountId()),
                AccountId.of(request.toAccountId()),
                request.amountMinorUnits()));
        TransferResponse body = TransferResponse.from(result);
        return ResponseEntity.created(URI.create("/transactions/" + body.transactionId()))
                .body(body);
    }
}
