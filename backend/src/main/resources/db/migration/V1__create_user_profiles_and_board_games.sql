CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    auth0_issuer VARCHAR(255) NOT NULL,
    auth0_subject VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_app_users_auth0_identity UNIQUE (auth0_issuer, auth0_subject)
);

CREATE TABLE board_games (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    name VARCHAR(200) NOT NULL,
    min_players INTEGER,
    max_players INTEGER,
    play_time_minutes INTEGER,
    notes VARCHAR(4000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_board_games_owner FOREIGN KEY (owner_id)
        REFERENCES app_users (id) ON DELETE CASCADE,
    CONSTRAINT ck_board_games_min_players CHECK (min_players IS NULL OR min_players > 0),
    CONSTRAINT ck_board_games_max_players CHECK (max_players IS NULL OR max_players > 0),
    CONSTRAINT ck_board_games_player_range CHECK (
        min_players IS NULL OR max_players IS NULL OR max_players >= min_players
    ),
    CONSTRAINT ck_board_games_play_time CHECK (play_time_minutes IS NULL OR play_time_minutes > 0)
);

CREATE INDEX ix_board_games_owner_id ON board_games (owner_id);