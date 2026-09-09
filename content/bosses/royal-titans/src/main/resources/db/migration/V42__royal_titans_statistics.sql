-- Immutable events are the authority. Account autosaves must not replace these records.
CREATE TABLE royal_titan_completions (
    encounter_id UUID PRIMARY KEY,
    elapsed_ticks INTEGER NOT NULL CHECK (elapsed_ticks > 0),
    completed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE royal_titan_participants (
    encounter_id UUID NOT NULL REFERENCES royal_titan_completions(encounter_id),
    account_id INTEGER NOT NULL REFERENCES accounts(id),
    display_name TEXT NOT NULL,
    personal_best_eligible BOOLEAN NOT NULL,
    previous_best_ticks INTEGER CHECK (previous_best_ticks > 0),
    PRIMARY KEY (encounter_id, account_id)
);
CREATE INDEX royal_titan_participants_account ON royal_titan_participants(account_id);

CREATE TABLE royal_titan_deaths (
    death_event_id UUID PRIMARY KEY,
    account_id INTEGER NOT NULL REFERENCES accounts(id),
    -- A death may precede completion, so this is intentionally not a completion foreign key.
    encounter_id UUID NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX royal_titan_deaths_account ON royal_titan_deaths(account_id);
