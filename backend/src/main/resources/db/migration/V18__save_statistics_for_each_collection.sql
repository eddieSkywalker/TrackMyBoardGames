-- Keep account totals in statistics_summaries and store a separate answer for each shelf.
CREATE TABLE collection_statistics_summaries (
    collection_id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    collection_size BIGINT NOT NULL CHECK (collection_size >= 0),
    most_played JSONB,
    most_recently_played JSONB,
    monthly_plays JSONB NOT NULL CHECK (jsonb_typeof(monthly_plays) = 'object'),
    refreshed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_collection_statistics_owner FOREIGN KEY (collection_id, owner_id)
        REFERENCES game_collections(id, owner_id) ON DELETE CASCADE
);

CREATE INDEX ix_collection_statistics_owner ON collection_statistics_summaries(owner_id);

-- Keep the existing account calculation unchanged, then add the shelf calculation to its usual entry point.
ALTER PROCEDURE refresh_collection_statistics(UUID, TIMESTAMP WITH TIME ZONE)
    RENAME TO refresh_account_statistics;

CREATE PROCEDURE refresh_owned_collection_statistics(
    IN p_owner_id UUID,
    IN p_refreshed_at TIMESTAMP WITH TIME ZONE
)
LANGUAGE SQL
SECURITY INVOKER
AS $$
    WITH game_totals AS (
        SELECT g.collection_id, g.id AS game_id, g.name, g.edition,
            count(p.id) AS play_count, max(p.played_on) AS last_played_on
        FROM board_games g
        LEFT JOIN game_plays p ON p.board_game_id = g.id
        WHERE g.owner_id = p_owner_id
        GROUP BY g.collection_id, g.id, g.name, g.edition
    ),
    game_summaries AS (
        SELECT collection_id, game_id, name, edition, play_count, last_played_on,
            jsonb_build_object(
                'gameId', game_id, 'name', name, 'edition', edition,
                'playCount', play_count, 'lastPlayedOn', last_played_on
            ) AS summary
        FROM game_totals
    ),
    monthly_totals AS (
        SELECT g.collection_id, to_char(p.played_on, 'YYYY-MM') AS month, count(*) AS play_count
        FROM game_plays p
        JOIN board_games g ON g.id = p.board_game_id
        WHERE g.owner_id = p_owner_id
        GROUP BY g.collection_id, to_char(p.played_on, 'YYYY-MM')
    )
    INSERT INTO collection_statistics_summaries
        (collection_id, owner_id, collection_size, most_played, most_recently_played, monthly_plays, refreshed_at)
    SELECT c.id, c.owner_id,
        (SELECT count(*) FROM game_totals g WHERE g.collection_id = c.id),
        (
            SELECT summary FROM game_summaries g
            WHERE g.collection_id = c.id AND play_count > 0
            ORDER BY play_count DESC, lower(name) COLLATE "C", lower(edition) COLLATE "C", game_id
            LIMIT 1
        ),
        (
            SELECT summary FROM game_summaries g
            WHERE g.collection_id = c.id AND last_played_on IS NOT NULL
            ORDER BY last_played_on DESC, lower(name) COLLATE "C", lower(edition) COLLATE "C", game_id
            LIMIT 1
        ),
        (
            SELECT coalesce(jsonb_object_agg(month, play_count), '{}'::jsonb)
            FROM monthly_totals m WHERE m.collection_id = c.id
        ),
        p_refreshed_at
    FROM game_collections c
    WHERE c.owner_id = p_owner_id
    ON CONFLICT (collection_id) DO UPDATE SET
        collection_size = EXCLUDED.collection_size,
        most_played = EXCLUDED.most_played,
        most_recently_played = EXCLUDED.most_recently_played,
        monthly_plays = EXCLUDED.monthly_plays,
        refreshed_at = EXCLUDED.refreshed_at;
$$;

-- Both sets of answers and the event receipt belong to the caller's single transaction.
CREATE PROCEDURE refresh_collection_statistics(
    IN p_owner_id UUID,
    IN p_refreshed_at TIMESTAMP WITH TIME ZONE
)
LANGUAGE plpgsql
SECURITY INVOKER
AS $$
BEGIN
    CALL refresh_account_statistics(p_owner_id, p_refreshed_at);
    CALL refresh_owned_collection_statistics(p_owner_id, p_refreshed_at);
END;
$$;

-- Give existing collections saved answers immediately, without waiting for another game or play event.
DO $$
DECLARE
    existing_owner UUID;
BEGIN
    FOR existing_owner IN SELECT id FROM app_users ORDER BY id FOR UPDATE
    LOOP
        CALL refresh_collection_statistics(existing_owner, current_timestamp);
    END LOOP;
END;
$$;
