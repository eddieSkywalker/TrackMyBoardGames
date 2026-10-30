-- Keep daily answers with the existing summaries so opening Track does not recount play history.
ALTER TABLE statistics_summaries
    ADD COLUMN daily_plays JSONB NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(daily_plays) = 'object');
ALTER TABLE collection_statistics_summaries
    ADD COLUMN daily_plays JSONB NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(daily_plays) = 'object');

CREATE OR REPLACE PROCEDURE refresh_collection_statistics(
    IN p_owner_id UUID,
    IN p_refreshed_at TIMESTAMP WITH TIME ZONE
)
LANGUAGE plpgsql
SECURITY INVOKER
AS $$
BEGIN
    -- Save the cards first, then daily counts, all within the caller's transaction.
    CALL refresh_account_statistics(p_owner_id, p_refreshed_at);
    CALL refresh_owned_collection_statistics(p_owner_id, p_refreshed_at);

    WITH daily_counts AS (
        SELECT p.played_on, count(*) AS play_count
        FROM game_plays p JOIN board_games g ON g.id = p.board_game_id
        WHERE g.owner_id = p_owner_id
        GROUP BY p.played_on
    )
    UPDATE statistics_summaries
    SET daily_plays = (SELECT coalesce(jsonb_object_agg(played_on::text, play_count), '{}'::jsonb) FROM daily_counts)
    WHERE owner_id = p_owner_id;

    WITH daily_counts AS (
        SELECT g.collection_id, p.played_on, count(*) AS play_count
        FROM game_plays p JOIN board_games g ON g.id = p.board_game_id
        WHERE g.owner_id = p_owner_id
        GROUP BY g.collection_id, p.played_on
    )
    UPDATE collection_statistics_summaries s
    SET daily_plays = (
        SELECT coalesce(jsonb_object_agg(played_on::text, play_count), '{}'::jsonb)
        FROM daily_counts d WHERE d.collection_id = s.collection_id
    )
    WHERE s.owner_id = p_owner_id;
END;
$$;

-- Fill daily answers for existing accounts once, without waiting for another event.
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
