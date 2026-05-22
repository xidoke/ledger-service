-- Accounts: one row per wallet (aggregate root, ADR-0010). `balance` is a cached
-- projection of ledger_entries (ADR-0006); `version` drives optimistic locking
-- (ADR-0011). `id` is an application-assigned UUID matching the domain AccountId.
-- `owner_ref` is null for system accounts such as SYSTEM_FUNDING.
CREATE TABLE accounts (
    id         UUID         PRIMARY KEY,
    owner_ref  VARCHAR(255),
    currency   CHAR(3)      NOT NULL,
    status     VARCHAR(16)  NOT NULL CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    balance    BIGINT       NOT NULL DEFAULT 0,
    version    BIGINT       NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Keep updated_at current on every row change.
CREATE OR REPLACE FUNCTION set_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER accounts_set_updated_at
    BEFORE UPDATE ON accounts
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
