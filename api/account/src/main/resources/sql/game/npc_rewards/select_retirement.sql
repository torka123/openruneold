SELECT a.reason, a.request_version, a.outcome,
       f.life_id, f.allocated_character_id, f.item_ordinal, f.count
FROM npc_reward_retirement_attempts a
LEFT JOIN npc_reward_retirement_fragments f ON f.attempt_id = a.attempt_id
WHERE a.attempt_id = ?
ORDER BY f.life_id, f.allocated_character_id, f.item_ordinal
