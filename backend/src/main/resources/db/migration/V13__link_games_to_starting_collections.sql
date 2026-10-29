-- Reuse existing shelf collections and backgrounds. Accounts without one get a starting collection.
INSERT INTO game_collections (id, owner_id, name, created_at)
SELECT gen_random_uuid(), u.id, 'My collection', u.created_at
FROM app_users u
WHERE NOT EXISTS (SELECT 1 FROM game_collections c WHERE c.owner_id = u.id);

ALTER TABLE game_collections
    ADD CONSTRAINT uq_game_collections_id_owner UNIQUE (id, owner_id);

ALTER TABLE app_users ADD COLUMN default_collection_id UUID;

UPDATE app_users u
SET default_collection_id = (
    SELECT c.id FROM game_collections c
    WHERE c.owner_id = u.id
    ORDER BY c.created_at, c.id
    LIMIT 1
);

-- New profiles are initialized inside one application transaction; the preference is temporarily null.
ALTER TABLE app_users ADD CONSTRAINT fk_app_users_default_collection
    FOREIGN KEY (default_collection_id, id)
    REFERENCES game_collections (id, owner_id);

ALTER TABLE board_games ADD COLUMN collection_id UUID;
UPDATE board_games g
SET collection_id = u.default_collection_id
FROM app_users u WHERE u.id = g.owner_id;
ALTER TABLE board_games ALTER COLUMN collection_id SET NOT NULL;
ALTER TABLE board_games ADD CONSTRAINT fk_board_games_collection_owner
    FOREIGN KEY (collection_id, owner_id)
    REFERENCES game_collections (id, owner_id);
CREATE INDEX ix_board_games_collection_id ON board_games (collection_id);

-- Keep the existing owner/name/edition uniqueness rule until game APIs are collection-scoped.
