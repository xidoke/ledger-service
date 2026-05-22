-- Account type drives the balance policy (ADR-0009): USER wallets may not overdraw; SYSTEM accounts may go negative.
-- Existing rows default to USER. (Reverses the earlier "no account_type column" note in ADR-0009 — see the updated ADR.)
ALTER TABLE accounts
    ADD COLUMN account_type VARCHAR(16) NOT NULL DEFAULT 'USER' CHECK (account_type IN ('USER', 'SYSTEM'));

-- Seed the SYSTEM_FUNDING account — the double-entry counterpart for top-ups (ADR-0009). Well-known id,
-- no owner, single-currency (ADR-0008). Its balance is the negative of the total user holdings.
INSERT INTO accounts (id, owner_ref, currency, status, balance, version, account_type)
VALUES ('00000000-0000-0000-0000-000000000001', NULL, 'USD', 'ACTIVE', 0, 0, 'SYSTEM');
