CREATE TABLE IF NOT EXISTS clan_event_notification_claims (
    event_id BIGINT NOT NULL REFERENCES clan_events (id) ON DELETE CASCADE,
    character_id INTEGER NOT NULL REFERENCES account_characters (id) ON DELETE CASCADE,
    notified_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (event_id, character_id)
);

CREATE INDEX IF NOT EXISTS idx_clan_event_notification_claims_character
    ON clan_event_notification_claims (character_id, notified_at DESC);
