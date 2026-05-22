package com.xidoke.ledger.transfer.adapter.in;

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

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    ResponseEntity<TransferResponse> transfer(@Valid @RequestBody TransferRequest request) {
        TransferResult result = transferService.transfer(
                AccountId.of(request.fromAccountId()), AccountId.of(request.toAccountId()), request.amountMinorUnits());
        TransferResponse body = TransferResponse.from(result);
        return ResponseEntity.created(URI.create("/transactions/" + body.transactionId()))
                .body(body);
    }
}
