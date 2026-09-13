-- Both writers hold the same V43 source locks before this fresh READ COMMITTED statement.
WITH source AS (SELECT ?::uuid AS life_id, ?::integer AS character_id, ?::integer AS ordinal)
SELECT
    (SELECT COALESCE(SUM(c.count), 0) FROM npc_reward_claim_consumption c, source s
     WHERE c.life_id = s.life_id AND c.allocated_character_id = s.character_id
       AND c.item_ordinal = s.ordinal)
    +
    (SELECT COALESCE(SUM(r.count), 0) FROM npc_reward_retirement_consumption r, source s
     WHERE r.life_id = s.life_id AND r.allocated_character_id = s.character_id
       AND r.item_ordinal = s.ordinal)
