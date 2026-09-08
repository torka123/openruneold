-- Clans created after this migration must complete the five-founder set in the same transaction.
-- Existing rows are intentionally not swept: this closes future insert paths without assuming
-- historical data is safe to rewrite.
CREATE OR REPLACE FUNCTION assert_clan_founder_count(target_clan_id BIGINT)
RETURNS VOID AS $$
DECLARE founder_count INTEGER;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM clans WHERE id = target_clan_id) THEN
        RETURN;
    END IF;
    PERFORM id FROM clans WHERE id = target_clan_id FOR UPDATE;
    SELECT COUNT(*) INTO founder_count FROM clan_founders WHERE clan_id = target_clan_id;
    IF founder_count <> 5 THEN
        RAISE EXCEPTION 'Clan % must persist exactly five founders', target_clan_id
            USING ERRCODE = '23514';
    END IF;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION enforce_clan_founder_count()
RETURNS TRIGGER AS $$
DECLARE target_clan_id BIGINT;
BEGIN
    target_clan_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.clan_id ELSE NEW.clan_id END;
    PERFORM assert_clan_founder_count(target_clan_id);
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION enforce_clan_insert_founder_count()
RETURNS TRIGGER AS $$
BEGIN
    PERFORM assert_clan_founder_count(NEW.id);
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS clans_exact_founder_count ON clans;
CREATE CONSTRAINT TRIGGER clans_exact_founder_count
AFTER INSERT ON clans DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION enforce_clan_insert_founder_count();
