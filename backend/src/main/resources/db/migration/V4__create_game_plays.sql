CREATE TABLE game_plays (
    id UUID PRIMARY KEY,
    board_game_id UUID NOT NULL,
    played_on DATE NOT NULL,
    duration_minutes INTEGER,
    player_count INTEGER,
    notes VARCHAR(4000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_game_plays_board_game FOREIGN KEY (board_game_id)
        REFERENCES board_games (id) ON DELETE CASCADE,
    CONSTRAINT ck_game_plays_duration CHECK (duration_minutes IS NULL OR duration_minutes > 0),
    CONSTRAINT ck_game_plays_player_count CHECK (player_count IS NULL OR player_count > 0)
);

CREATE INDEX ix_game_plays_board_game_id_played_on ON game_plays (board_game_id, played_on DESC);
