ALTER TABLE friend_chat_settings
    ALTER COLUMN enabled SET DEFAULT FALSE;

-- V27 introduced settings during this deployment. Rows created before this correction were
-- implicit placeholders, not player-configured channels, so keep them disabled until named.
UPDATE friend_chat_settings
SET enabled = FALSE;
