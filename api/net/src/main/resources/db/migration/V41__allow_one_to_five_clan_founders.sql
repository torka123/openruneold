-- Allow the owner to create a clan alone while retaining the native five-slot founding UI.
-- Both deferred founder-count triggers installed by V33/V35 call this helper, so replacing it
-- updates existing databases without modifying already-applied migrations.
CREATE OR REPLACE FUNCTION assert_clan_founder_count(target_clan_id BIGINT)
RETURNS VOID AS $$
DECLARE founder_count INTEGER;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM clans WHERE id = target_clan_id) THEN
        RETURN;
    END IF;
    PERFORM id FROM clans WHERE id = target_clan_id FOR UPDATE;
    SELECT COUNT(*) INTO founder_count FROM clan_founders WHERE clan_id = target_clan_id;
    IF founder_count NOT BETWEEN 1 AND 5 THEN
        RAISE EXCEPTION 'Clan % must persist between one and five founders', target_clan_id
            USING ERRCODE = '23514';
    END IF;
END;
$$ LANGUAGE plpgsql;
