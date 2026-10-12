-- A QR label code is a random, unguessable token printed on a game's label.
-- The QR code only holds the token, never a game id, so codes can be revoked without changing the game.
-- owner_id is the app user who made the label; it lets us list all of one user's labels.
CREATE TABLE qr_label_codes (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    board_game_id UUID NOT NULL,
    token VARCHAR(64) NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_qr_label_codes_token UNIQUE (token),
    CONSTRAINT fk_qr_label_codes_owner FOREIGN KEY (owner_id)
        REFERENCES app_users (id) ON DELETE CASCADE,
    CONSTRAINT fk_qr_label_codes_game FOREIGN KEY (board_game_id)
        REFERENCES board_games (id) ON DELETE CASCADE
);

CREATE INDEX idx_qr_label_codes_owner ON qr_label_codes (owner_id);
CREATE INDEX idx_qr_label_codes_game ON qr_label_codes (board_game_id);