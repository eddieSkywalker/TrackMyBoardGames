-- Keep V14 unchanged for databases that already applied it. Move access details onto the collection.
ALTER TABLE game_collections
    ADD COLUMN permanent_tier VARCHAR(20) NOT NULL DEFAULT 'FREE',
    ADD COLUMN subscription_status VARCHAR(20) NOT NULL DEFAULT 'INACTIVE',
    ADD COLUMN subscription_ends_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN total_payments BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN subscriber_months BIGINT NOT NULL DEFAULT 0;

UPDATE game_collections c
SET permanent_tier = e.permanent_tier,
    subscription_status = e.subscription_status,
    subscription_ends_at = e.subscription_ends_at
FROM collection_entitlements e WHERE e.collection_id = c.id;

ALTER TABLE game_collections
    ADD CONSTRAINT ck_collections_permanent_tier CHECK (permanent_tier IN ('FREE', 'HOME', 'PRO')),
    ADD CONSTRAINT ck_collections_subscription_status CHECK (subscription_status IN ('ACTIVE', 'INACTIVE')),
    ADD CONSTRAINT ck_collections_active_subscription CHECK (
        subscription_status <> 'ACTIVE' OR (plan_tier = 'BUSINESS' AND subscription_ends_at IS NOT NULL)),
    ADD CONSTRAINT ck_collections_payment_totals CHECK (total_payments >= 0 AND subscriber_months >= 0);

DROP TABLE collection_entitlements;

-- Amounts are whole USD cents, not floating-point dollars. Never invent payments for migrated access rights.
CREATE TABLE collection_payments (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    collection_id UUID,
    payment_reference VARCHAR(200) NOT NULL UNIQUE,
    amount_cents BIGINT NOT NULL CHECK (amount_cents > 0),
    currency VARCHAR(3) NOT NULL CHECK (currency = 'USD'),
    paid_at TIMESTAMP WITH TIME ZONE NOT NULL,
    business_period_reference VARCHAR(200),
    business_access_ends_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_collection_payments_owner FOREIGN KEY (collection_id, owner_id)
        REFERENCES game_collections(id, owner_id) ON DELETE SET NULL (collection_id),
    CONSTRAINT uq_collection_paid_period UNIQUE (collection_id, business_period_reference),
    CONSTRAINT ck_collection_paid_period CHECK (
        (business_period_reference IS NULL AND business_access_ends_at IS NULL)
        OR (business_period_reference IS NOT NULL AND business_access_ends_at IS NOT NULL
            AND business_access_ends_at > paid_at))
);

CREATE INDEX ix_collection_payments_owner ON collection_payments(owner_id);
