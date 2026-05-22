package com.xidoke.ledger.account.adapter.in;

import com.xidoke.ledger.account.application.AccountService;
import com.xidoke.ledger.account.domain.Account;
import com.xidoke.ledger.common.domain.AccountId;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Inbound REST adapter for the account use cases. Holds no business logic — delegates to {@link AccountService}. */
@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request) {
        Account account = accountService.create(request.ownerRef(), request.currency());
        AccountResponse body = AccountResponse.from(account);
        return ResponseEntity.created(URI.create("/accounts/" + body.id())).body(body);
    }

    @GetMapping("/{id}")
    AccountResponse get(@PathVariable UUID id) {
        return AccountResponse.from(accountService.get(AccountId.of(id)));
    }

    @GetMapping("/{id}/entries")
    List<LedgerEntryResponse> entries(@PathVariable UUID id) {
        return accountService.listEntries(AccountId.of(id)).stream()
                .map(LedgerEntryResponse::from)
                .toList();
    }
}
