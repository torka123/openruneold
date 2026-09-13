INSERT INTO npc_reward_claim_consumption
    (attempt_id, life_id, allocated_character_id, item_ordinal, count, outcome)
SELECT attempt_id, life_id, allocated_character_id, item_ordinal, count, 'COMMITTED'
FROM npc_reward_claim_fragments WHERE attempt_id = ?
