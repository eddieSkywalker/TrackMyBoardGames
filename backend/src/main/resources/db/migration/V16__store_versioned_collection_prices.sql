-- Each row is a complete price list. Changing prices means adding a new row with a start date and a reason.
CREATE TABLE collection_price_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    effective_from TIMESTAMP WITH TIME ZONE NOT NULL UNIQUE,
    home_cents BIGINT NOT NULL CHECK (home_cents > 0),
    pro_cents BIGINT NOT NULL CHECK (pro_cents > 0),
    business_monthly_cents BIGINT NOT NULL CHECK (business_monthly_cents > 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'USD' CHECK (currency = 'USD'),
    change_reason VARCHAR(500) NOT NULL CHECK (length(trim(change_reason)) > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT current_timestamp
);

-- Flyway runs this seed once. Later deployments leave the saved prices alone.
INSERT INTO collection_price_versions
    (effective_from, home_cents, pro_cents, business_monthly_cents, change_reason)
VALUES (current_timestamp, 3000, 5000, 3000, 'Initial collection prices');

-- Keep old prices available so past payments can still point to the prices used at the time.
CREATE FUNCTION prevent_collection_price_rewrite() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Saved prices cannot be changed or removed. Add a new price version instead.'
        USING ERRCODE = '23514';
END;
$$;

CREATE TRIGGER protect_collection_price_history
BEFORE UPDATE OR DELETE OR TRUNCATE ON collection_price_versions
FOR EACH STATEMENT EXECUTE FUNCTION prevent_collection_price_rewrite();

-- Older payments have no known price version. Leave them empty rather than inventing their history.
ALTER TABLE collection_payments
    ADD COLUMN price_version_id UUID REFERENCES collection_price_versions(id);

CREATE INDEX ix_collection_payments_price_version ON collection_payments(price_version_id);

CREATE FUNCTION require_collection_payment_price_version() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.price_version_id IS NULL THEN
        RAISE EXCEPTION 'New payments must identify the price version used.'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER check_new_payment_price_version
BEFORE INSERT ON collection_payments
FOR EACH ROW EXECUTE FUNCTION require_collection_payment_price_version();
