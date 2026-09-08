ALTER TABLE clan_broadcast_settings
    DROP CONSTRAINT IF EXISTS clan_broadcast_settings_minimum_combat_achievement_tier_check;

ALTER TABLE clan_broadcast_settings
    ADD CONSTRAINT clan_broadcast_settings_minimum_combat_achievement_tier_check
    CHECK (minimum_combat_achievement_tier BETWEEN -1 AND 5);
