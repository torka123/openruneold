-- Request identity must remain settleable even when its live picker/source no longer exists.
-- Only the successful consumption rows reference live V43 allocation items.
CREATE TABLE npc_reward_claim_attempts (
    attempt_id UUID PRIMARY KEY,
    picker_account_id INTEGER NOT NULL CHECK (picker_account_id > 0),
    picker_character_id INTEGER NOT NULL CHECK (picker_character_id > 0),
    request_version INTEGER NOT NULL CHECK (request_version = 1),
    destination TEXT NOT NULL CHECK (destination = 'inventory'),
    outcome TEXT NOT NULL CHECK (outcome IN ('COMMITTED', 'ABORTED')),
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (attempt_id, outcome)
);

CREATE TABLE npc_reward_claim_fragments (
    attempt_id UUID NOT NULL REFERENCES npc_reward_claim_attempts(attempt_id),
    life_id UUID NOT NULL,
    allocated_character_id INTEGER NOT NULL CHECK (allocated_character_id > 0),
    item_ordinal INTEGER NOT NULL CHECK (item_ordinal >= 0),
    count INTEGER NOT NULL CHECK (count > 0),
    PRIMARY KEY (attempt_id, life_id, allocated_character_id, item_ordinal),
    UNIQUE (attempt_id, life_id, allocated_character_id, item_ordinal, count)
);

CREATE TABLE npc_reward_claim_consumption (
    attempt_id UUID NOT NULL,
    life_id UUID NOT NULL,
    allocated_character_id INTEGER NOT NULL,
    item_ordinal INTEGER NOT NULL,
    count INTEGER NOT NULL CHECK (count > 0),
    outcome TEXT NOT NULL CHECK (outcome = 'COMMITTED'),
    PRIMARY KEY (attempt_id, life_id, allocated_character_id, item_ordinal),
    FOREIGN KEY (attempt_id, outcome) REFERENCES npc_reward_claim_attempts(attempt_id, outcome),
    FOREIGN KEY (attempt_id, life_id, allocated_character_id, item_ordinal, count)
        REFERENCES npc_reward_claim_fragments(attempt_id, life_id, allocated_character_id, item_ordinal, count),
    FOREIGN KEY (life_id, allocated_character_id, item_ordinal)
        REFERENCES npc_reward_items(life_id, character_id, ordinal)
);
CREATE INDEX npc_reward_claim_consumption_source
    ON npc_reward_claim_consumption(life_id, allocated_character_id, item_ordinal);
