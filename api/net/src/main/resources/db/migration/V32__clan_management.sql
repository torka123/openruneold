ALTER TABLE clans
    ADD COLUMN IF NOT EXISTS description VARCHAR(120) NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS recruitment_open BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS applications_open BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS minimum_combat_level SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS minimum_total_level SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS invite_rank SMALLINT NOT NULL DEFAULT 100,
    ADD COLUMN IF NOT EXISTS application_review_rank SMALLINT NOT NULL DEFAULT 100,
    ADD COLUMN IF NOT EXISTS settings_change_rank SMALLINT NOT NULL DEFAULT 125,
    ADD COLUMN IF NOT EXISTS owner_transfer_character_id INTEGER REFERENCES account_characters (id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS owner_transfer_available_at TIMESTAMP;

CREATE TABLE IF NOT EXISTS clan_founders (
    clan_id BIGINT NOT NULL REFERENCES clans (id) ON DELETE CASCADE,
    character_id INTEGER NOT NULL REFERENCES account_characters (id) ON DELETE RESTRICT,
    display_name_at_creation VARCHAR(12) NOT NULL,
    founder_order SMALLINT NOT NULL CHECK (founder_order BETWEEN 0 AND 4),
    PRIMARY KEY (clan_id, character_id),
    UNIQUE (clan_id, founder_order)
);

CREATE TABLE IF NOT EXISTS clan_creation_history (
    character_id INTEGER NOT NULL REFERENCES account_characters (id) ON DELETE CASCADE,
    creation_rune_day INTEGER NOT NULL,
    clan_id BIGINT NOT NULL REFERENCES clans (id) ON DELETE CASCADE,
    PRIMARY KEY (character_id, creation_rune_day)
);

CREATE TABLE IF NOT EXISTS clan_applications (
    clan_id BIGINT NOT NULL REFERENCES clans (id) ON DELETE CASCADE,
    character_id INTEGER NOT NULL REFERENCES account_characters (id) ON DELETE CASCADE,
    message VARCHAR(80) NOT NULL DEFAULT '',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (clan_id, character_id)
);

CREATE INDEX IF NOT EXISTS idx_clan_applications_created
    ON clan_applications (clan_id, created_at, character_id);

CREATE TABLE IF NOT EXISTS clan_invites (
    clan_id BIGINT NOT NULL REFERENCES clans (id) ON DELETE CASCADE,
    character_id INTEGER NOT NULL REFERENCES account_characters (id) ON DELETE CASCADE,
    actor_character_id INTEGER NOT NULL REFERENCES account_characters (id) ON DELETE CASCADE,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (clan_id, character_id)
);

CREATE INDEX IF NOT EXISTS idx_clan_invites_target
    ON clan_invites (character_id, expires_at);
