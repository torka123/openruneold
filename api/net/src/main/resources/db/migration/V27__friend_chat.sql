CREATE TABLE IF NOT EXISTS friend_chat_settings (
    owner_character_id INTEGER PRIMARY KEY REFERENCES account_characters (id) ON DELETE CASCADE,
    channel_name VARCHAR(12) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    join_rank SMALLINT NOT NULL DEFAULT -1 CHECK (join_rank BETWEEN -1 AND 7),
    talk_rank SMALLINT NOT NULL DEFAULT -1 CHECK (talk_rank BETWEEN -1 AND 7),
    kick_rank SMALLINT NOT NULL DEFAULT 7 CHECK (kick_rank BETWEEN -1 AND 7),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS friend_chat_ranks (
    owner_character_id INTEGER NOT NULL REFERENCES account_characters (id) ON DELETE CASCADE,
    target_character_id INTEGER NOT NULL REFERENCES account_characters (id) ON DELETE CASCADE,
    rank SMALLINT NOT NULL CHECK (rank BETWEEN 0 AND 6),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (owner_character_id, target_character_id),
    CONSTRAINT chk_friend_chat_rank_not_self CHECK (owner_character_id <> target_character_id)
);

CREATE INDEX IF NOT EXISTS idx_friend_chat_ranks_target
    ON friend_chat_ranks (target_character_id);
