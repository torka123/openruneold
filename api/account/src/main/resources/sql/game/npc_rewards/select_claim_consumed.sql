SELECT COALESCE(SUM(count), 0) FROM npc_reward_claim_consumption
WHERE life_id = ? AND allocated_character_id = ? AND item_ordinal = ?
