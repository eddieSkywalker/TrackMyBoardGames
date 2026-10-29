-- The caller owns the transaction, owner lock, and duplicate-event receipt.
-- Do not add COMMIT or ROLLBACK inside this procedure.
CREATE PROCEDURE refresh_collection_statistics(
    IN p_owner_id UUID,
    IN p_refreshed_at TIMESTAMP WITH TIME ZONE
)
LANGUAGE SQL
SECURITY INVOKER
AS $$
    WITH game_totals AS (
        SELECT g.id AS game_id, g.name, g.edition,
            count(p.id) AS play_count, max(p.played_on) AS last_played_on
        FROM board_games g
        LEFT JOIN game_plays p ON p.board_game_id = g.id
        WHERE g.owner_id = p_owner_id
        GROUP BY g.id, g.name, g.edition
    ),
    game_summaries AS (
        SELECT game_id, name, edition, play_count, last_played_on,
            jsonb_build_object(
                'gameId', game_id,
                'name', name,
                'edition', edition,
                'playCount', play_count,
                'lastPlayedOn', last_played_on
            ) AS summary
        FROM game_totals
    ),
    monthly_totals AS (
        SELECT to_char(p.played_on, 'YYYY-MM') AS month, count(*) AS play_count
        FROM game_plays p
        JOIN board_games g ON g.id = p.board_game_id
        WHERE g.owner_id = p_owner_id
        GROUP BY to_char(p.played_on, 'YYYY-MM')
    )
    INSERT INTO statistics_summaries (
        owner_id, collection_size, most_played, most_recently_played, monthly_plays, refreshed_at
    )
    VALUES (
        p_owner_id,
        (SELECT count(*) FROM game_totals),
        (
            SELECT summary FROM game_summaries
            WHERE play_count > 0
            ORDER BY play_count DESC, lower(name) COLLATE "C", lower(edition) COLLATE "C", game_id
            LIMIT 1
        ),
        (
            SELECT summary FROM game_summaries
            WHERE last_played_on IS NOT NULL
            ORDER BY last_played_on DESC, lower(name) COLLATE "C", lower(edition) COLLATE "C", game_id
            LIMIT 1
        ),
        (SELECT coalesce(jsonb_object_agg(month, play_count), '{}'::jsonb) FROM monthly_totals),
        p_refreshed_at
    )
    ON CONFLICT (owner_id) DO UPDATE SET
        collection_size = EXCLUDED.collection_size,
        most_played = EXCLUDED.most_played,
        most_recently_played = EXCLUDED.most_recently_played,
        monthly_plays = EXCLUDED.monthly_plays,
        refreshed_at = EXCLUDED.refreshed_at;
$$;
