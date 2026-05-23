-- Idempotent-consumer inbox (ADR-0013, LDG-55). A consumer records each event id it has processed; the unique PK plus
-- INSERT … ON CONFLICT DO NOTHING make "have I processed this?" an atomic claim, so an at-least-once redelivery runs
-- the side effect exactly once. `event_id` is the outbox row id — a stable, publisher-assigned id (dedup by
-- publisher/business identity, not a per-delivery broker id).
CREATE TABLE processed_events (
    event_id     BIGINT      PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
