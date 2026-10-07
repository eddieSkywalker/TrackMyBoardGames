ALTER TABLE board_games
    DROP CONSTRAINT ck_board_games_best_players,
    ALTER COLUMN best_players TYPE VARCHAR(20) USING best_players::VARCHAR;
