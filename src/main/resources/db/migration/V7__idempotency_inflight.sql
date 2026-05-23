-- In-flight handling for idempotency keys (ADR-0012, LDG-49). A request now CLAIMS its key up front by inserting a
-- PENDING row (INSERT … ON CONFLICT DO NOTHING); the response columns are filled only once the operation completes,
-- so they become nullable. A concurrent request that loses the claim sees the PENDING row and is rejected 409.
ALTER TABLE idempotency_keys
    ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'COMPLETED' CHECK (status IN ('PENDING', 'COMPLETED'));

ALTER TABLE idempotency_keys ALTER COLUMN response_status DROP NOT NULL;
ALTER TABLE idempotency_keys ALTER COLUMN response_body DROP NOT NULL;
