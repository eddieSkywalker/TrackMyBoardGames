-- Keep receipts independent of the outbox so removing published history cannot allow repeat processing.
CREATE TABLE statistics_processed_events (
    event_id UUID PRIMARY KEY,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL
);
