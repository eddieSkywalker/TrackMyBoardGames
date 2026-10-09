-- Image storage:
--  * a game's cover photo lives directly on its board_games row;
--  * the collection background lives on a game_collections row (one default collection per user for now,
--    but the table allows several collections per user later).

-- Both columns are empty (NULL) when the game has no cover photo.
ALTER TABLE board_games ADD COLUMN cover_content_type VARCHAR(50);
ALTER TABLE board_games ADD COLUMN cover_image BYTEA;

CREATE TABLE game_collections (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    name VARCHAR(200) NOT NULL,
    background_content_type VARCHAR(50),
    background_image BYTEA,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_game_collections_owner FOREIGN KEY (owner_id)
        REFERENCES app_users (id) ON DELETE CASCADE
);

CREATE INDEX idx_game_collections_owner ON game_collections (owner_id);