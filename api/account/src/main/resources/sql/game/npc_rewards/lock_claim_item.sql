SELECT count FROM npc_reward_items
WHERE life_id = ? AND character_id = ? AND ordinal = ?
FOR UPDATE
