INSERT INTO npc_reward_claim_attempts
    (attempt_id, picker_account_id, picker_character_id, request_version, destination, outcome)
VALUES (?, ?, ?, 1, 'inventory', ?)
ON CONFLICT (attempt_id) DO NOTHING
