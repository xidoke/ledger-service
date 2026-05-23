-- Transactional outbox (ADR-0013, LDG-54). Each posting (transfer/topup) inserts one event row in the SAME DB
-- transaction as the ledger write, so an event can never be lost (commit-but-no-event) nor orphaned
-- (event-but-rollback) — the dual-write problem is removed by making the event part of the same atomic commit.
-- A separate poller (LDG-55) publishes PENDING rows and marks them SENT; `id` is monotonic so it doubles as the
-- delivery order.
CREATE TABLE outbox (
    id             BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    aggregate_id   UUID        NOT NULL, -- the transaction the event is about
    event_type     VARCHAR(64) NOT NULL,
    payload        JSONB       NOT NULL,
    status         VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'SENT')),
    schema_version INTEGER     NOT NULL DEFAULT 1,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ
);

-- Poller hot path: oldest unsent events first. Partial index stays tiny as rows are marked SENT.
CREATE INDEX idx_outbox_pending ON outbox (id) WHERE status = 'PENDING';
