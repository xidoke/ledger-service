package com.xidoke.ledger.account.application;

import com.xidoke.ledger.account.domain.Account;
import com.xidoke.ledger.account.domain.AccountNotFoundException;
import com.xidoke.ledger.account.domain.AccountRepository;
import com.xidoke.ledger.account.domain.LedgerEntryQueryRepository;
import com.xidoke.ledger.common.domain.AccountId;
import com.xidoke.ledger.common.domain.LedgerEntry;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/** Application service orchestrating the account use cases over the domain ports. */
@Service
public class AccountService {

    private final AccountRepository accounts;
    private final LedgerEntryQueryRepository entryQuery;

    public AccountService(AccountRepository accounts, LedgerEntryQueryRepository entryQuery) {
        this.accounts = accounts;
        this.entryQuery = entryQuery;
    }

    public Account create(@Nullable String ownerRef, String currencyCode) {
        return accounts.save(Account.open(AccountId.newId(), ownerRef, currencyCode));
    }

    public Account get(AccountId id) {
        return accounts.findById(id).orElseThrow(() -> new AccountNotFoundException(id));
    }

    public List<LedgerEntry> listEntries(AccountId id) {
        Account account = get(id); // 404 if the account does not exist
        return entryQuery.findByAccount(id, account.currencyCode());
    }
}
