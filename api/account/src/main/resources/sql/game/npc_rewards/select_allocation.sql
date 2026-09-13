SELECT l.source_npc_key, r.character_id, r.account_id, r.drop_coords, r.duration,
       r.destination, i.ordinal, i.obj_key, i.count, i.vars
FROM npc_reward_lives l
LEFT JOIN npc_reward_recipients r ON r.life_id = l.life_id
LEFT JOIN npc_reward_items i ON i.life_id = r.life_id AND i.character_id = r.character_id
WHERE l.life_id = ?
ORDER BY r.character_id, i.ordinal
