-- Stripe-style idempotency keys (ADR-0012). A mutating request may carry an `Idempotency-Key` header; the first
-- request is processed and its response stored here, and any retry with the same key replays the stored response
-- instead of re-running the side effect. `request_hash` lets the server reject a key reused with a different body.
CREATE TABLE idempotency_keys (
    key             VARCHAR(255) PRIMARY KEY,
    request_hash    CHAR(64)    NOT NULL, -- SHA-256 hex of method + path + body
    response_status INTEGER     NOT NULL,
    response_body   TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
