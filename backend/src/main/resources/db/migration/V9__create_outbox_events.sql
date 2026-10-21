-- Keep identifiers as values, not cascading foreign keys: deletion events must survive their source records.
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    event_version INTEGER NOT NULL,
    owner_id UUID NOT NULL,
    aggregate_id UUID NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    claim_token UUID,
    claimed_until TIMESTAMP WITH TIME ZONE,
    last_error VARCHAR(2000),
    CONSTRAINT ck_outbox_events_type CHECK (length(trim(event_type)) > 0),
    CONSTRAINT ck_outbox_events_version CHECK (event_version > 0),
    CONSTRAINT ck_outbox_events_payload CHECK (jsonb_typeof(payload) = 'object'),
    CONSTRAINT ck_outbox_events_attempt_count CHECK (attempt_count >= 0),
    CONSTRAINT ck_outbox_events_claim CHECK (
        (claim_token IS NULL AND claimed_until IS NULL)
        OR (claim_token IS NOT NULL AND claimed_until IS NOT NULL)
    )
);

-- Published history does not need to be scanned when finding the next batch to send.
CREATE INDEX ix_outbox_events_pending
    ON outbox_events (next_attempt_at, occurred_at, id)
    WHERE published_at IS NULL;
