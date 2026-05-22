-- Transactions: the posting header that groups >= 2 balanced ledger entries
-- (ADR-0005). `id` is an application-assigned UUID matching the domain
-- TransactionId. The `idempotency_key` column is added later with the
-- idempotency feature (ADR-0012), not here.
CREATE TABLE transactions (
    id         UUID         PRIMARY KEY,
    type       VARCHAR(16)  NOT NULL CHECK (type IN ('TOPUP', 'TRANSFER')),
    status     VARCHAR(16)  NOT NULL CHECK (status IN ('PENDING', 'POSTED')),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
