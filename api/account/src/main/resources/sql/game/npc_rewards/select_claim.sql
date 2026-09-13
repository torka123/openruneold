SELECT a.picker_account_id, a.picker_character_id, a.request_version, a.destination, a.outcome,
       f.life_id, f.allocated_character_id, f.item_ordinal, f.count
FROM npc_reward_claim_attempts a
LEFT JOIN npc_reward_claim_fragments f ON f.attempt_id = a.attempt_id
WHERE a.attempt_id = ?
ORDER BY f.life_id, f.allocated_character_id, f.item_ordinal
