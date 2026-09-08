ALTER TABLE clan_hall_party_chest_items
    ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX IF NOT EXISTS clan_hall_party_chest_active_idx
    ON clan_hall_party_chest_items (clan_id, item_id)
    WHERE active;

CREATE TABLE IF NOT EXISTS clan_hall_party_chest_cleanup_archive (
    id BIGSERIAL PRIMARY KEY,
    clan_id BIGINT NOT NULL REFERENCES clans (id) ON DELETE CASCADE,
    item_id INTEGER NOT NULL CHECK (item_id >= 0),
    quantity BIGINT NOT NULL CHECK (quantity BETWEEN 1 AND 2147483647),
    deposited_by INTEGER REFERENCES account_characters (id) ON DELETE SET NULL,
    deposited_at TIMESTAMP WITH TIME ZONE NOT NULL,
    hall_session_id VARCHAR(64) NOT NULL,
    cleanup_reason VARCHAR(64) NOT NULL,
    cleaned_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS clan_hall_party_chest_cleanup_clan_idx
    ON clan_hall_party_chest_cleanup_archive (clan_id, cleaned_at DESC);

CREATE OR REPLACE FUNCTION enforce_clan_party_chest_limit()
RETURNS TRIGGER AS $$
DECLARE item_count INTEGER;
BEGIN
    PERFORM clan_id FROM clan_hall_settings WHERE clan_id = NEW.clan_id FOR UPDATE;
    SELECT COUNT(*) INTO item_count
    FROM clan_hall_party_chest_items
    WHERE clan_id = NEW.clan_id AND active;
    IF item_count > 216 THEN
        RAISE EXCEPTION 'Clan % exceeds the 216 unique party chest item limit', NEW.clan_id
            USING ERRCODE = '23514';
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;
