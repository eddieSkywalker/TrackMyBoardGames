CREATE TABLE statistics_summaries (
    owner_id UUID PRIMARY KEY REFERENCES app_users (id) ON DELETE CASCADE,
    collection_size BIGINT NOT NULL CHECK (collection_size >= 0),
    most_played JSONB,
    most_recently_played JSONB,
    monthly_plays JSONB NOT NULL CHECK (jsonb_typeof(monthly_plays) = 'object'),
    refreshed_at TIMESTAMP WITH TIME ZONE NOT NULL
);
