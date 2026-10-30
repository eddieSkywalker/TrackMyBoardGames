-- Each row holds the game limits for all tiers. A change requires a new row with a start date and a reason.
CREATE TABLE collection_limit_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    effective_from TIMESTAMP WITH TIME ZONE NOT NULL UNIQUE,
    free_limit INTEGER NOT NULL CHECK (free_limit > 0),
    home_limit INTEGER NOT NULL CHECK (home_limit > 0),
    pro_limit INTEGER NOT NULL CHECK (pro_limit > 0),
    business_limit INTEGER NOT NULL CHECK (business_limit > 0),
    change_reason VARCHAR(500) NOT NULL CHECK (length(trim(change_reason)) > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT current_timestamp
);

-- These starting limits are inserted once. Later deployments leave the saved limits alone.
INSERT INTO collection_limit_versions
    (effective_from, free_limit, home_limit, pro_limit, business_limit, change_reason)
VALUES (current_timestamp, 20, 100, 200, 1000, 'Initial collection game limits');

CREATE FUNCTION prevent_collection_limit_rewrite() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Saved game limits cannot be changed or removed. Add a new limit version instead.'
        USING ERRCODE = '23514';
END;
$$;

CREATE TRIGGER protect_collection_limit_history
BEFORE UPDATE OR DELETE OR TRUNCATE ON collection_limit_versions
FOR EACH STATEMENT EXECUTE FUNCTION prevent_collection_limit_rewrite();
