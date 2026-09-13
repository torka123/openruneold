SELECT l.source_npc_key, r.character_id, r.account_id, r.drop_coords, r.duration,
       r.destination, i.ordinal, i.obj_key, i.count, i.vars,
       (SELECT COALESCE(SUM(c.count), 0) FROM npc_reward_claim_consumption c
        WHERE c.life_id = l.life_id AND c.allocated_character_id = r.character_id
          AND c.item_ordinal = i.ordinal) AS claimed_count,
       (SELECT COALESCE(SUM(t.count), 0) FROM npc_reward_retirement_consumption t
        WHERE t.life_id = l.life_id AND t.allocated_character_id = r.character_id
          AND t.item_ordinal = i.ordinal) AS retired_count
FROM npc_reward_lives l
LEFT JOIN npc_reward_recipients r ON r.life_id = l.life_id
LEFT JOIN npc_reward_items i ON i.life_id = r.life_id AND i.character_id = r.character_id
WHERE l.life_id = ?
ORDER BY r.character_id, i.ordinal
