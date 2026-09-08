CREATE TABLE IF NOT EXISTS account_attrs (
    account_id INTEGER NOT NULL
        REFERENCES accounts(id) ON DELETE CASCADE,
    attr_key TEXT NOT NULL,
    value_json TEXT NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (account_id, attr_key)
);
