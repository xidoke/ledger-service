-- Ledger entries: append-only, immutable double-entry facts (ADR-0005). `amount`
-- is always positive (minor units, ADR-0007); `direction` carries the sign. The
-- surrogate `id` is DB-generated because the domain LedgerEntry has no identity of
-- its own (it is a value/fact keyed by its components).
CREATE TABLE ledger_entries (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID         NOT NULL REFERENCES transactions (id),
    account_id     UUID         NOT NULL REFERENCES accounts (id),
    direction      VARCHAR(8)   NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
    amount         BIGINT       NOT NULL CHECK (amount > 0),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Account history: entries for an account in time order.
CREATE INDEX idx_ledger_entries_account_created ON ledger_entries (account_id, created_at);

-- Enforce append-only at the DB layer: posted entries are immutable. Corrections
-- are new reversing entries (INSERT), never UPDATE/DELETE (ADR-0005).
CREATE OR REPLACE FUNCTION reject_ledger_entry_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'ledger_entries is append-only: % is not allowed', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER ledger_entries_append_only
    BEFORE UPDATE OR DELETE ON ledger_entries
    FOR EACH ROW
    EXECUTE FUNCTION reject_ledger_entry_mutation();
