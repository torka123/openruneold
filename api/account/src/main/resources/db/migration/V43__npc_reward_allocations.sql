-- Immutable allocations are independent of autosaved character attributes and inventories.
-- These tables record personal-ground intent only; they do not grant or claim any objects.
CREATE TABLE npc_reward_lives (
    life_id UUID PRIMARY KEY,
    source_npc_key TEXT NOT NULL CHECK (length(btrim(source_npc_key)) > 0),
    allocated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE npc_reward_recipients (
    life_id UUID NOT NULL REFERENCES npc_reward_lives(life_id),
    character_id INTEGER NOT NULL REFERENCES account_characters(id),
    account_id INTEGER NOT NULL REFERENCES accounts(id),
    drop_coords INTEGER NOT NULL,
    duration INTEGER NOT NULL CHECK (duration >= 0),
    destination TEXT NOT NULL CHECK (destination = 'personal_ground'),
    PRIMARY KEY (life_id, character_id)
);
CREATE INDEX npc_reward_recipients_character ON npc_reward_recipients(character_id);

CREATE TABLE npc_reward_items (
    life_id UUID NOT NULL,
    character_id INTEGER NOT NULL,
    ordinal INTEGER NOT NULL CHECK (ordinal >= 0),
    obj_key TEXT NOT NULL CHECK (length(btrim(obj_key)) > 0),
    count INTEGER NOT NULL CHECK (count > 0),
    vars INTEGER NOT NULL,
    PRIMARY KEY (life_id, character_id, ordinal),
    FOREIGN KEY (life_id, character_id) REFERENCES npc_reward_recipients(life_id, character_id)
);
