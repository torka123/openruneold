CREATE TABLE IF NOT EXISTS clans (
    id BIGSERIAL PRIMARY KEY,
    normalized_name VARCHAR(12) NOT NULL UNIQUE,
    display_name VARCHAR(12) NOT NULL,
    owner_character_id INTEGER NOT NULL UNIQUE REFERENCES account_characters (id) ON DELETE RESTRICT,
    creation_rune_day INTEGER NOT NULL,
    home_world SMALLINT NOT NULL DEFAULT 1,
    allow_guests BOOLEAN NOT NULL DEFAULT TRUE,
    guests_can_talk BOOLEAN NOT NULL DEFAULT TRUE,
    talk_rank SMALLINT NOT NULL DEFAULT -1,
    kick_rank SMALLINT NOT NULL DEFAULT 100,
    ban_rank SMALLINT NOT NULL DEFAULT 100,
    expel_rank SMALLINT NOT NULL DEFAULT 125,
    assign_rank SMALLINT NOT NULL DEFAULT 100,
    edit_permissions_rank SMALLINT NOT NULL DEFAULT 125,
    state_version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS clan_members (
    clan_id BIGINT NOT NULL REFERENCES clans (id) ON DELETE CASCADE,
    character_id INTEGER NOT NULL UNIQUE REFERENCES account_characters (id) ON DELETE CASCADE,
    rank SMALLINT NOT NULL DEFAULT 0 CHECK (rank BETWEEN 0 AND 126),
    title_slot SMALLINT NOT NULL DEFAULT 0 CHECK (title_slot BETWEEN 0 AND 14),
    muted BOOLEAN NOT NULL DEFAULT FALSE,
    joined_rune_day INTEGER NOT NULL,
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (clan_id, character_id)
);

CREATE INDEX IF NOT EXISTS idx_clan_members_clan_rank
    ON clan_members (clan_id, rank DESC, character_id);

CREATE TABLE IF NOT EXISTS clan_rank_titles (
    clan_id BIGINT NOT NULL REFERENCES clans (id) ON DELETE CASCADE,
    rank_slot SMALLINT NOT NULL CHECK (rank_slot BETWEEN 0 AND 14),
    title_id INTEGER NOT NULL DEFAULT 0,
    title_name VARCHAR(20) NOT NULL,
    PRIMARY KEY (clan_id, rank_slot)
);

CREATE TABLE IF NOT EXISTS clan_bans (
    clan_id BIGINT NOT NULL REFERENCES clans (id) ON DELETE CASCADE,
    character_id INTEGER NOT NULL REFERENCES account_characters (id) ON DELETE CASCADE,
    actor_character_id INTEGER REFERENCES account_characters (id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (clan_id, character_id)
);

CREATE TABLE IF NOT EXISTS clan_guest_kicks (
    clan_id BIGINT NOT NULL REFERENCES clans (id) ON DELETE CASCADE,
    character_id INTEGER NOT NULL REFERENCES account_characters (id) ON DELETE CASCADE,
    expires_at TIMESTAMP NOT NULL,
    PRIMARY KEY (clan_id, character_id)
);

CREATE TABLE IF NOT EXISTS social_channel_affinity (
    character_id INTEGER PRIMARY KEY REFERENCES account_characters (id) ON DELETE CASCADE,
    friend_chat_owner_id INTEGER REFERENCES account_characters (id) ON DELETE SET NULL,
    guest_clan_id BIGINT REFERENCES clans (id) ON DELETE SET NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS friend_chat_kicks (
    owner_character_id INTEGER NOT NULL REFERENCES account_characters (id) ON DELETE CASCADE,
    character_id INTEGER NOT NULL REFERENCES account_characters (id) ON DELETE CASCADE,
    expires_at TIMESTAMP NOT NULL,
    PRIMARY KEY (owner_character_id, character_id)
);

CREATE TABLE IF NOT EXISTS social_chat_presence (
    character_id INTEGER PRIMARY KEY REFERENCES account_characters (id) ON DELETE CASCADE,
    world_id SMALLINT NOT NULL,
    friend_chat_owner_id INTEGER REFERENCES account_characters (id) ON DELETE SET NULL,
    own_clan_id BIGINT REFERENCES clans (id) ON DELETE SET NULL,
    guest_clan_id BIGINT REFERENCES clans (id) ON DELETE SET NULL,
    session_generation BIGINT NOT NULL,
    touched_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_social_presence_friend_chat
    ON social_chat_presence (friend_chat_owner_id) WHERE friend_chat_owner_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_social_presence_own_clan
    ON social_chat_presence (own_clan_id) WHERE own_clan_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_social_presence_guest_clan
    ON social_chat_presence (guest_clan_id) WHERE guest_clan_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS social_chat_events (
    id BIGSERIAL PRIMARY KEY,
    request_id UUID NOT NULL UNIQUE,
    source_world_id SMALLINT NOT NULL,
    channel_kind SMALLINT NOT NULL CHECK (channel_kind BETWEEN 0 AND 2),
    channel_id BIGINT NOT NULL,
    event_kind SMALLINT NOT NULL CHECK (event_kind BETWEEN 0 AND 3),
    actor_character_id INTEGER REFERENCES account_characters (id) ON DELETE SET NULL,
    actor_name VARCHAR(12) NOT NULL DEFAULT '',
    actor_crown SMALLINT NOT NULL DEFAULT 0,
    world_message_counter INTEGER NOT NULL DEFAULT 0,
    payload VARCHAR(160) NOT NULL DEFAULT '',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_social_chat_events_poll
    ON social_chat_events (id, source_world_id);

CREATE TABLE IF NOT EXISTS social_chat_world_offsets (
    world_id SMALLINT PRIMARY KEY,
    last_event_id BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
