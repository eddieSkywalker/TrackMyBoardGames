ALTER TABLE board_games
    ADD COLUMN best_players INTEGER,
    ADD CONSTRAINT ck_board_games_best_players CHECK (best_players IS NULL OR best_players > 0);