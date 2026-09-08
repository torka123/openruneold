-- Revision-240 clan domain and durable workflow state.
-- The Owner constraint is deferred so multi-statement creation/transfer can commit atomically.

ALTER TABLE clan_members DROP CONSTRAINT IF EXISTS clan_members_rank_check;
ALTER TABLE clan_members
    ADD CONSTRAINT clan_members_rank_check CHECK (rank IN (
        0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 95, 96, 97, 98, 99,
        100, 101, 102, 103, 104, 105, 110, 115, 120, 124, 125, 126
    ));
ALTER TABLE clan_members DROP CONSTRAINT IF EXISTS clan_members_title_slot_check;
ALTER TABLE clan_members
    ADD CONSTRAINT clan_members_title_slot_check CHECK (title_slot BETWEEN 0 AND 23),
    ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS last_seen_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS last_seen_visible BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE clan_rank_titles DROP CONSTRAINT IF EXISTS clan_rank_titles_rank_slot_check;
ALTER TABLE clan_rank_titles
    ADD CONSTRAINT clan_rank_titles_rank_slot_check CHECK (rank_slot BETWEEN 0 AND 23),
    ALTER COLUMN title_name TYPE VARCHAR(40);

ALTER TABLE clans
    ADD COLUMN IF NOT EXISTS name_changed_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS owner_resignation_requested_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS motif_primary_colour SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS motif_secondary_colour SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS motif_primary_symbol SMALLINT,
    ADD COLUMN IF NOT EXISTS motif_secondary_symbol SMALLINT,
    ADD COLUMN IF NOT EXISTS guest_minimum_combat_level SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS guest_minimum_total_level SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS guest_minimum_quest_points SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS guest_iron_requirement SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE clans
    ADD CONSTRAINT clans_motif_primary_colour_check CHECK (motif_primary_colour BETWEEN 0 AND 7),
    ADD CONSTRAINT clans_motif_secondary_colour_check CHECK (motif_secondary_colour BETWEEN 0 AND 7),
    ADD CONSTRAINT clans_motif_primary_symbol_check CHECK (
        motif_primary_symbol IS NULL OR motif_primary_symbol BETWEEN 0 AND 26
    ),
    ADD CONSTRAINT clans_motif_secondary_symbol_check CHECK (
        motif_secondary_symbol IS NULL OR motif_secondary_symbol BETWEEN 0 AND 26
    ),
    ADD CONSTRAINT clans_guest_minimum_combat_check CHECK (guest_minimum_combat_level BETWEEN 0 AND 126),
    ADD CONSTRAINT clans_guest_minimum_total_check CHECK (guest_minimum_total_level BETWEEN 0 AND 2500),
    ADD CONSTRAINT clans_guest_minimum_quest_points_check CHECK (
        guest_minimum_quest_points BETWEEN 0 AND 32767
    ),
    ADD CONSTRAINT clans_guest_iron_requirement_check CHECK (guest_iron_requirement BETWEEN 0 AND 3);

CREATE TABLE IF NOT EXISTS clan_creation_drafts (
    owner_character_id INTEGER PRIMARY KEY REFERENCES account_characters (id) ON DELETE CASCADE,
    normalized_name VARCHAR(12) NOT NULL,
    display_name VARCHAR(12) NOT NULL,
    home_world SMALLINT NOT NULL CHECK (home_world > 0),
    state_version BIGINT NOT NULL DEFAULT 1 CHECK (state_version > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS clan_creation_founder_invitations (
    draft_owner_character_id INTEGER NOT NULL REFERENCES clan_creation_drafts (owner_character_id) ON DELETE CASCADE,
    founder_character_id INTEGER NOT NULL REFERENCES account_characters (id) ON DELETE CASCADE,
    founder_order SMALLINT NOT NULL CHECK (founder_order BETWEEN 1 AND 4),
    invitation_state SMALLINT NOT NULL DEFAULT 0 CHECK (invitation_state BETWEEN 0 AND 2),
    authoritative_playtime_ticks INTEGER NOT NULL DEFAULT 0 CHECK (authoritative_playtime_ticks >= 0),
    invited_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    responded_at TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (draft_owner_character_id, founder_character_id),
    UNIQUE (draft_owner_character_id, founder_order),
    CHECK (draft_owner_character_id <> founder_character_id)
);

CREATE TABLE IF NOT EXISTS clan_interests (
    clan_id BIGINT NOT NULL REFERENCES clans (id) ON DELETE CASCADE,
    interest_id SMALLINT NOT NULL CHECK (interest_id BETWEEN 0 AND 15),
    PRIMARY KEY (clan_id, interest_id)
);

CREATE TABLE IF NOT EXISTS clan_permission_settings (
    clan_id BIGINT NOT NULL REFERENCES clans (id) ON DELETE CASCADE,
    permission_key VARCHAR(40) NOT NULL CHECK (permission_key IN (
        'permission_access', 'edit_rank_titles', 'assign_member_ranks', 'recruitment',
        'expel_members', 'edit_public_profile', 'edit_motif', 'talk_in_chat', 'kick_guests',
        'ban_guests', 'organise_events', 'initiate_clan_wars', 'coffer_deposit',
        'coffer_withdraw', 'hall_enter', 'hall_edit_settings', 'hall_party_chest_deposit',
        'hall_pull_lever', 'hall_party_balloon_loot'
    )),
    minimum_rank SMALLINT NOT NULL CHECK (minimum_rank IN (
        -1, 0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 95, 96, 97, 98, 99,
        100, 101, 102, 103, 104, 105, 110, 115, 120, 124, 125, 126
    )),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (clan_id, permission_key)
);

CREATE TABLE IF NOT EXISTS clan_events (
    id BIGSERIAL PRIMARY KEY,
    clan_id BIGINT NOT NULL REFERENCES clans (id) ON DELETE CASCADE,
    event_type SMALLINT NOT NULL CHECK (event_type BETWEEN 0 AND 3),
    event_subtype SMALLINT NOT NULL CHECK (event_subtype BETWEEN 0 AND 9),
    activity_key VARCHAR(48) NOT NULL,
    starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    duration_ticks BIGINT NOT NULL CHECK (duration_ticks BETWEEN 1 AND 4320000),
    minimum_join_rank SMALLINT NOT NULL CHECK (minimum_join_rank IN (
        -1, 0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 95, 96, 97, 98, 99,
        100, 101, 102, 103, 104, 105, 110, 115, 120, 124, 125, 126
    )),
    world_id SMALLINT NOT NULL CHECK (world_id > 0),
    created_by INTEGER REFERENCES account_characters (id) ON DELETE SET NULL,
    updated_by INTEGER REFERENCES account_characters (id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_clan_events_upcoming ON clan_events (clan_id, starts_at, id);

CREATE TABLE IF NOT EXISTS clan_coffers (
    clan_id BIGINT PRIMARY KEY REFERENCES clans (id) ON DELETE CASCADE,
    balance BIGINT NOT NULL DEFAULT 0 CHECK (balance BETWEEN 0 AND 50000000),
    updated_by INTEGER REFERENCES account_characters (id) ON DELETE SET NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS clan_coffer_transactions (
    id BIGSERIAL PRIMARY KEY,
    request_id UUID NOT NULL UNIQUE,
    clan_id BIGINT NOT NULL REFERENCES clans (id) ON DELETE CASCADE,
    actor_character_id INTEGER REFERENCES account_characters (id) ON DELETE SET NULL,
    transaction_type SMALLINT NOT NULL CHECK (transaction_type IN (0, 1)),
    amount BIGINT NOT NULL CHECK (amount BETWEEN 1 AND 50000000),
    balance_after BIGINT NOT NULL CHECK (balance_after BETWEEN 0 AND 50000000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_clan_coffer_transactions_history
    ON clan_coffer_transactions (clan_id, id DESC);

CREATE TABLE IF NOT EXISTS clan_hall_settings (
    clan_id BIGINT PRIMARY KEY REFERENCES clans (id) ON DELETE CASCADE,
    home_world_only BOOLEAN NOT NULL DEFAULT FALSE,
    locked BOOLEAN NOT NULL DEFAULT FALSE,
    player_capacity SMALLINT NOT NULL DEFAULT 2000 CHECK (player_capacity BETWEEN 1 AND 2000),
    theme_key VARCHAR(32) NOT NULL DEFAULT 'default' CHECK (theme_key ~ '^[a-z0-9_]{1,32}$'),
    identity_key VARCHAR(32) NOT NULL DEFAULT 'standard' CHECK (identity_key ~ '^[a-z0-9_]{1,32}$'),
    edit_state_version BIGINT NOT NULL DEFAULT 0 CHECK (edit_state_version >= 0),
    edit_session_character_id INTEGER REFERENCES account_characters (id) ON DELETE SET NULL,
    edit_session_started_at TIMESTAMP WITH TIME ZONE,
    edit_session_expires_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (
        (edit_session_character_id IS NULL AND edit_session_started_at IS NULL AND edit_session_expires_at IS NULL) OR
        (edit_session_character_id IS NOT NULL AND edit_session_started_at IS NOT NULL AND
         edit_session_expires_at IS NOT NULL AND edit_session_expires_at > edit_session_started_at)
    )
);

CREATE TABLE IF NOT EXISTS clan_hall_party_chest_items (
    clan_id BIGINT NOT NULL REFERENCES clans (id) ON DELETE CASCADE,
    item_id INTEGER NOT NULL CHECK (item_id >= 0),
    quantity BIGINT NOT NULL CHECK (quantity BETWEEN 1 AND 2147483647),
    deposited_by INTEGER REFERENCES account_characters (id) ON DELETE SET NULL,
    deposited_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (clan_id, item_id)
);

CREATE TABLE IF NOT EXISTS clan_broadcast_settings (
    clan_id BIGINT PRIMARY KEY REFERENCES clans (id) ON DELETE CASCADE,
    level_interval SMALLINT NOT NULL DEFAULT 10 CHECK (level_interval IN (1, 5, 10)),
    post_99_xp_interval INTEGER NOT NULL DEFAULT 10000000 CHECK (post_99_xp_interval > 0),
    collection_interval SMALLINT NOT NULL DEFAULT 1 CHECK (collection_interval IN (1, 10, 25, 50)),
    minimum_loot_value INTEGER NOT NULL DEFAULT 0 CHECK (minimum_loot_value >= 0),
    minimum_player_kill_value INTEGER NOT NULL DEFAULT 0 CHECK (minimum_player_kill_value >= 0),
    minimum_loot_key_value INTEGER NOT NULL DEFAULT 0 CHECK (minimum_loot_key_value >= 0),
    minimum_combat_achievement_tier SMALLINT NOT NULL DEFAULT 0 CHECK (
        minimum_combat_achievement_tier BETWEEN 0 AND 5
    ),
    main_game_worlds_only BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS clan_broadcast_permissions (
    clan_id BIGINT NOT NULL REFERENCES clans (id) ON DELETE CASCADE,
    broadcast_key VARCHAR(40) NOT NULL CHECK (broadcast_key IN (
        'membership', 'quest_completion', 'achievement_diary', 'collection_log',
        'hardcore_status', 'personal_best', 'level_up', 'post_99_xp', 'loot_or_pet',
        'combat_achievement_tier', 'combat_achievement_task', 'player_kill',
        'player_defeat', 'loot_key', 'league_shared', 'league_relic_tier',
        'league_area_unlock', 'league_rank', 'combat_mastery'
    )),
    minimum_rank SMALLINT NOT NULL CHECK (minimum_rank IN (
        0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 95, 96, 97, 98, 99,
        100, 101, 102, 103, 104, 105, 110, 115, 120, 124, 125, 126
    )),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (clan_id, broadcast_key)
);

CREATE TABLE IF NOT EXISTS clan_member_notification_preferences (
    clan_id BIGINT NOT NULL,
    character_id INTEGER NOT NULL,
    broadcasts_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    event_reminders_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    recruiter_notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    last_daily_event_reminder_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (clan_id, character_id),
    FOREIGN KEY (clan_id, character_id)
        REFERENCES clan_members (clan_id, character_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS clan_recruiter_notifications (
    id BIGSERIAL PRIMARY KEY,
    clan_id BIGINT NOT NULL REFERENCES clans (id) ON DELETE CASCADE,
    recipient_character_id INTEGER NOT NULL REFERENCES account_characters (id) ON DELETE CASCADE,
    applicant_character_id INTEGER REFERENCES account_characters (id) ON DELETE SET NULL,
    notification_kind SMALLINT NOT NULL CHECK (notification_kind IN (0, 1)),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    delivered_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX IF NOT EXISTS idx_clan_recruiter_notifications_delivery
    ON clan_recruiter_notifications (recipient_character_id, delivered_at, id);

CREATE OR REPLACE FUNCTION seed_canonical_clan_settings(target_clan_id BIGINT)
RETURNS VOID AS $$
BEGIN
    INSERT INTO clan_permission_settings (clan_id, permission_key, minimum_rank)
    VALUES
        (target_clan_id, 'permission_access', 125), (target_clan_id, 'edit_rank_titles', 100),
        (target_clan_id, 'assign_member_ranks', 100), (target_clan_id, 'recruitment', 100),
        (target_clan_id, 'expel_members', 125), (target_clan_id, 'edit_public_profile', 125),
        (target_clan_id, 'edit_motif', 100), (target_clan_id, 'talk_in_chat', -1),
        (target_clan_id, 'kick_guests', 100), (target_clan_id, 'ban_guests', 100),
        (target_clan_id, 'organise_events', 100), (target_clan_id, 'initiate_clan_wars', 100),
        (target_clan_id, 'coffer_deposit', 0), (target_clan_id, 'coffer_withdraw', 100),
        (target_clan_id, 'hall_enter', -1), (target_clan_id, 'hall_edit_settings', 100),
        (target_clan_id, 'hall_party_chest_deposit', 0), (target_clan_id, 'hall_pull_lever', 100),
        (target_clan_id, 'hall_party_balloon_loot', 0)
    ON CONFLICT (clan_id, permission_key) DO NOTHING;
    INSERT INTO clan_coffers (clan_id) VALUES (target_clan_id) ON CONFLICT DO NOTHING;
    INSERT INTO clan_hall_settings (clan_id) VALUES (target_clan_id) ON CONFLICT DO NOTHING;
    INSERT INTO clan_broadcast_settings (clan_id) VALUES (target_clan_id) ON CONFLICT DO NOTHING;
    INSERT INTO clan_broadcast_permissions (clan_id, broadcast_key, minimum_rank)
    SELECT target_clan_id, broadcast_keys.key, 0
    FROM unnest(ARRAY[
        'membership', 'quest_completion', 'achievement_diary', 'collection_log',
        'hardcore_status', 'personal_best', 'level_up', 'post_99_xp', 'loot_or_pet',
        'combat_achievement_tier', 'combat_achievement_task', 'player_kill',
        'player_defeat', 'loot_key', 'league_shared', 'league_relic_tier',
        'league_area_unlock', 'league_rank', 'combat_mastery'
    ]::TEXT[]) AS broadcast_keys(key)
    ON CONFLICT DO NOTHING;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION seed_canonical_clan_settings_trigger()
RETURNS TRIGGER AS $$
BEGIN
    PERFORM seed_canonical_clan_settings(NEW.id);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS clans_seed_canonical_settings ON clans;
CREATE TRIGGER clans_seed_canonical_settings AFTER INSERT ON clans
FOR EACH ROW EXECUTE FUNCTION seed_canonical_clan_settings_trigger();
SELECT seed_canonical_clan_settings(id) FROM clans;

CREATE OR REPLACE FUNCTION seed_clan_member_preferences()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO clan_member_notification_preferences (clan_id, character_id)
    VALUES (NEW.clan_id, NEW.character_id) ON CONFLICT DO NOTHING;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS clan_members_seed_notification_preferences ON clan_members;
CREATE TRIGGER clan_members_seed_notification_preferences AFTER INSERT ON clan_members
FOR EACH ROW EXECUTE FUNCTION seed_clan_member_preferences();
INSERT INTO clan_member_notification_preferences (clan_id, character_id)
SELECT clan_id, character_id FROM clan_members ON CONFLICT DO NOTHING;

CREATE OR REPLACE FUNCTION assert_canonical_clan_state(target_clan_id BIGINT)
RETURNS VOID AS $$
DECLARE
    header_owner INTEGER;
    member_count INTEGER;
    owner_count INTEGER;
    ranked_owner INTEGER;
BEGIN
    SELECT owner_character_id INTO header_owner FROM clans
    WHERE id = target_clan_id FOR UPDATE;
    IF NOT FOUND THEN RETURN; END IF;
    SELECT COUNT(*), COUNT(*) FILTER (WHERE rank = 126),
           MIN(character_id) FILTER (WHERE rank = 126)
    INTO member_count, owner_count, ranked_owner
    FROM clan_members WHERE clan_id = target_clan_id;
    IF member_count > 500 THEN
        RAISE EXCEPTION 'Clan % exceeds the 500 member limit', target_clan_id USING ERRCODE = '23514';
    END IF;
    IF owner_count <> 1 OR ranked_owner IS DISTINCT FROM header_owner THEN
        RAISE EXCEPTION 'Clan % must have exactly one Owner matching owner_character_id', target_clan_id
            USING ERRCODE = '23514';
    END IF;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION enforce_canonical_clan_state()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_TABLE_NAME = 'clan_members' THEN
        IF TG_OP <> 'INSERT' THEN PERFORM assert_canonical_clan_state(OLD.clan_id); END IF;
        IF TG_OP = 'INSERT' OR (TG_OP = 'UPDATE' AND NEW.clan_id IS DISTINCT FROM OLD.clan_id) THEN
            PERFORM assert_canonical_clan_state(NEW.clan_id);
        END IF;
    ELSE
        PERFORM assert_canonical_clan_state(NEW.id);
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

DO $$ DECLARE clan_record RECORD; BEGIN
    FOR clan_record IN SELECT id FROM clans LOOP
        PERFORM assert_canonical_clan_state(clan_record.id);
    END LOOP;
END; $$;
DROP TRIGGER IF EXISTS clan_members_canonical_state ON clan_members;
CREATE CONSTRAINT TRIGGER clan_members_canonical_state
AFTER INSERT OR UPDATE OR DELETE ON clan_members DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION enforce_canonical_clan_state();
DROP TRIGGER IF EXISTS clans_canonical_owner_state ON clans;
CREATE CONSTRAINT TRIGGER clans_canonical_owner_state
AFTER INSERT OR UPDATE ON clans DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION enforce_canonical_clan_state();

CREATE OR REPLACE FUNCTION enforce_clan_founder_count()
RETURNS TRIGGER AS $$
DECLARE
    target_clan_id BIGINT;
    founder_count INTEGER;
BEGIN
    target_clan_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.clan_id ELSE NEW.clan_id END;
    IF NOT EXISTS (SELECT 1 FROM clans WHERE id = target_clan_id) THEN
        RETURN NULL;
    END IF;

    PERFORM id FROM clans WHERE id = target_clan_id FOR UPDATE;
    SELECT COUNT(*) INTO founder_count FROM clan_founders WHERE clan_id = target_clan_id;
    IF founder_count <> 5 THEN
        RAISE EXCEPTION 'Clan % must persist exactly five founders', target_clan_id USING ERRCODE = '23514';
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS clan_founders_exact_count ON clan_founders;
CREATE CONSTRAINT TRIGGER clan_founders_exact_count
AFTER INSERT OR UPDATE OR DELETE ON clan_founders DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION enforce_clan_founder_count();

CREATE OR REPLACE FUNCTION enforce_clan_ban_limit()
RETURNS TRIGGER AS $$
DECLARE ban_count INTEGER;
BEGIN
    PERFORM id FROM clans WHERE id = NEW.clan_id FOR UPDATE;
    SELECT COUNT(*) INTO ban_count FROM clan_bans WHERE clan_id = NEW.clan_id;
    IF ban_count > 100 THEN
        RAISE EXCEPTION 'Clan % exceeds the 100 ban limit', NEW.clan_id USING ERRCODE = '23514';
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS clan_bans_limit ON clan_bans;
CREATE CONSTRAINT TRIGGER clan_bans_limit AFTER INSERT OR UPDATE ON clan_bans
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION enforce_clan_ban_limit();

CREATE OR REPLACE FUNCTION enforce_clan_party_chest_limit()
RETURNS TRIGGER AS $$
DECLARE item_count INTEGER;
BEGIN
    PERFORM clan_id FROM clan_hall_settings WHERE clan_id = NEW.clan_id FOR UPDATE;
    SELECT COUNT(*) INTO item_count FROM clan_hall_party_chest_items WHERE clan_id = NEW.clan_id;
    IF item_count > 216 THEN
        RAISE EXCEPTION 'Clan % exceeds the 216 unique party chest item limit', NEW.clan_id
            USING ERRCODE = '23514';
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS clan_party_chest_item_limit ON clan_hall_party_chest_items;
CREATE CONSTRAINT TRIGGER clan_party_chest_item_limit
AFTER INSERT OR UPDATE ON clan_hall_party_chest_items DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION enforce_clan_party_chest_limit();
