-- A user can own only one game per name and edition (ignoring upper/lower case).
-- This is the database's safety net behind the check in BoardGameService.
CREATE UNIQUE INDEX ux_board_games_owner_name_edition
    ON board_games (owner_id, LOWER(name), LOWER(edition));
