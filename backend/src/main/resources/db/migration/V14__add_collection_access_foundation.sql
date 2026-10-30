-- Existing collections begin as Free; no payment or subscription is inferred from old records.
ALTER TABLE game_collections
    ADD COLUMN plan_tier VARCHAR(20) NOT NULL DEFAULT 'FREE',
    ADD CONSTRAINT ck_game_collections_plan_tier CHECK (plan_tier IN ('FREE', 'HOME', 'PRO', 'BUSINESS'));

-- Permanent rights survive Business expiry. Allowances live in application configuration, not these rows.
CREATE TABLE collection_entitlements (
    collection_id UUID PRIMARY KEY REFERENCES game_collections(id) ON DELETE CASCADE,
    permanent_tier VARCHAR(20) NOT NULL DEFAULT 'FREE',
    subscription_status VARCHAR(20) NOT NULL DEFAULT 'INACTIVE',
    subscription_ends_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_collection_entitlements_permanent_tier CHECK (permanent_tier IN ('FREE', 'HOME', 'PRO')),
    CONSTRAINT ck_collection_entitlements_subscription_status CHECK (subscription_status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_collection_entitlements_business_access CHECK (
        subscription_status <> 'ACTIVE' OR (subscription_ends_at IS NOT NULL AND permanent_tier = 'PRO')
    )
);

INSERT INTO collection_entitlements (collection_id)
SELECT id FROM game_collections;
