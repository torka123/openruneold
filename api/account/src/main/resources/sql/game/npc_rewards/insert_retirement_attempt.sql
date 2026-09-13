INSERT INTO npc_reward_retirement_attempts (attempt_id, reason, request_version, outcome)
VALUES (?, ?, 1, ?)
ON CONFLICT (attempt_id) DO NOTHING
