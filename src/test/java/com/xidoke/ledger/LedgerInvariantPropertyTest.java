package com.xidoke.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import com.xidoke.ledger.account.domain.Account;
import com.xidoke.ledger.common.domain.AccountId;
import com.xidoke.ledger.common.domain.Direction;
import com.xidoke.ledger.common.domain.LedgerEntry;
import com.xidoke.ledger.common.domain.Money;
import com.xidoke.ledger.common.domain.TransactionId;
import com.xidoke.ledger.ledger.domain.Transaction;
import com.xidoke.ledger.ledger.domain.TransactionType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based test of the core double-entry invariants (ADR-0005). Instead of a few hand-picked examples, jqwik
 * generates ~500 random sequences of top-ups and transfers, applies each through the real domain aggregates
 * ({@code Account.debit/credit} + {@code Transaction.post}), and asserts after every sequence that the books still
 * balance. Runs as a pure-domain test (no Spring, no database) so a thousand operations finish in milliseconds; on a
 * failure jqwik shrinks the operation list to a minimal counter-example and prints the seed for a deterministic re-run.
 *
 * <p>Three invariants, at three levels:
 *
 * <ol>
 *   <li>per posting + globally: {@code Σ DEBIT == Σ CREDIT} across every entry produced;
 *   <li>cache vs projection: each account's cached {@code balance} equals the signed sum of its entries (credit +,
 *       debit −) — the balance-as-projection consistency (ADR-0006);
 *   <li>system-wide: {@code balance(SYSTEM_FUNDING) + Σ balance(users) == 0} — money is only ever moved, never created.
 * </ol>
 */
class LedgerInvariantPropertyTest {

    private static final String USD = "USD";
    private static final int USER_COUNT = 3;
    private static final Instant AT = Instant.parse("2026-05-23T00:00:00Z");

    private enum Kind {
        TOPUP,
        TRANSFER
    }

    /** A generated operation over the fixed account set. For TOPUP only {@code a} + {@code amount} are used. */
    private record Op(Kind kind, int a, int b, long amount) {}

    @Provide
    Arbitrary<List<Op>> operations() {
        Arbitrary<Integer> index = Arbitraries.integers().between(0, USER_COUNT - 1);
        Arbitrary<Long> amount = Arbitraries.longs().between(1L, 1_000_000L);

        Arbitrary<Op> topup = Combinators.combine(index, amount).as((i, amt) -> new Op(Kind.TOPUP, i, i, amt));
        Arbitrary<Op> transfer =
                Combinators.combine(index, index, amount).as((from, to, amt) -> new Op(Kind.TRANSFER, from, to, amt));

        return Arbitraries.oneOf(topup, transfer).list().ofMaxSize(40);
    }

    @Property(tries = 500)
    void booksAlwaysBalance(@ForAll("operations") List<Op> ops) {
        Account funding = Account.openSystem(AccountId.newId(), USD);
        List<Account> users = new ArrayList<>();
        for (int i = 0; i < USER_COUNT; i++) {
            users.add(Account.open(AccountId.newId(), "owner-" + i, USD));
        }
        List<LedgerEntry> entries = new ArrayList<>();

        for (Op op : ops) {
            apply(op, funding, users, entries);
        }

        // (1) Σ DEBIT == Σ CREDIT across every entry produced
        long debitTotal = sumByDirection(entries, Direction.DEBIT);
        long creditTotal = sumByDirection(entries, Direction.CREDIT);
        assertThat(debitTotal).as("Σ DEBIT == Σ CREDIT").isEqualTo(creditTotal);

        // (2) each account's cached balance == signed sum of its entries (credit +, debit −)
        for (Account account : users) {
            assertThat(account.balance().minorUnits())
                    .as("balance == Σ signed entries for %s", account.id())
                    .isEqualTo(signedSumFor(entries, account.id()));
        }
        assertThat(funding.balance().minorUnits())
                .as("balance == Σ signed entries for SYSTEM_FUNDING")
                .isEqualTo(signedSumFor(entries, funding.id()));

        // (3) system-wide zero-sum: nothing is created, only moved
        long systemTotal = funding.balance().minorUnits()
                + users.stream().mapToLong(a -> a.balance().minorUnits()).sum();
        assertThat(systemTotal).as("Σ all balances == 0").isZero();
    }

    /** Applies a valid operation; silently skips ones the domain would reject (self-transfer, insufficient funds). */
    private void apply(Op op, Account funding, List<Account> users, List<LedgerEntry> entries) {
        Money amount = Money.of(op.amount(), USD);
        TransactionId txId = TransactionId.newId();
        Transaction tx = new Transaction(txId, transactionType(op));

        if (op.kind() == Kind.TOPUP) {
            Account user = users.get(op.a());
            tx.addEntry(funding.debit(amount, txId, AT)); // SYSTEM may go negative
            tx.addEntry(user.credit(amount, txId, AT));
        } else {
            if (op.a() == op.b()) {
                return; // self-transfer is rejected by the use case; nothing to apply
            }
            Account from = users.get(op.a());
            Account to = users.get(op.b());
            if (from.balance().isLessThan(amount)) {
                return; // would overdraw a user account — rejected, no posting
            }
            tx.addEntry(from.debit(amount, txId, AT));
            tx.addEntry(to.credit(amount, txId, AT));
        }

        tx.post(); // enforces Σ DEBIT == Σ CREDIT for this posting
        entries.addAll(tx.entries());
    }

    private static TransactionType transactionType(Op op) {
        return op.kind() == Kind.TOPUP ? TransactionType.TOPUP : TransactionType.TRANSFER;
    }

    private static long sumByDirection(List<LedgerEntry> entries, Direction direction) {
        return entries.stream()
                .filter(e -> e.direction() == direction)
                .mapToLong(e -> e.amount().minorUnits())
                .sum();
    }

    private static long signedSumFor(List<LedgerEntry> entries, AccountId accountId) {
        return entries.stream()
                .filter(e -> e.accountId().equals(accountId))
                .mapToLong(e -> e.direction() == Direction.CREDIT
                        ? e.amount().minorUnits()
                        : -e.amount().minorUnits())
                .sum();
    }
}
