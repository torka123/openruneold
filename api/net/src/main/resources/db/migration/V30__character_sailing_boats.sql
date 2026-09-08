CREATE TABLE character_sailing_boats (
    character_id INTEGER NOT NULL
        REFERENCES account_characters(id) ON DELETE CASCADE,
    slot SMALLINT NOT NULL CHECK (slot BETWEEN 0 AND 4),
    boat_type TEXT NOT NULL,
    format_version SMALLINT NOT NULL DEFAULT 1 CHECK (format_version >= 1),
    PRIMARY KEY (character_id, slot)
);
