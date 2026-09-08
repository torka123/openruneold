-- Persist every rev-240 native clan notification control without discarding legacy choices.
ALTER TABLE clan_broadcast_settings
    ADD COLUMN IF NOT EXISTS event_starting_notifications BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS level_every_ten_minimum SMALLINT NOT NULL DEFAULT 0 CHECK (level_every_ten_minimum BETWEEN 0 AND 99),
    ADD COLUMN IF NOT EXISTS level_every_five_minimum SMALLINT NOT NULL DEFAULT 0 CHECK (level_every_five_minimum BETWEEN 0 AND 99),
    ADD COLUMN IF NOT EXISTS level_every_one_minimum SMALLINT NOT NULL DEFAULT 0 CHECK (level_every_one_minimum BETWEEN 0 AND 99);

-- V33 exposed one interval. Carry it into the matching native threshold once.
UPDATE clan_broadcast_settings
SET level_every_ten_minimum = CASE WHEN level_interval = 10 THEN 50 ELSE level_every_ten_minimum END,
    level_every_five_minimum = CASE WHEN level_interval = 5 THEN 50 ELSE level_every_five_minimum END,
    level_every_one_minimum = CASE WHEN level_interval = 1 THEN 50 ELSE level_every_one_minimum END
WHERE level_every_ten_minimum = 0
  AND level_every_five_minimum = 0
  AND level_every_one_minimum = 0;

COMMENT ON COLUMN clan_broadcast_settings.event_starting_notifications IS 'Native setting 110.';
COMMENT ON COLUMN clan_broadcast_settings.level_every_ten_minimum IS 'Native setting 66; zero disables.';
COMMENT ON COLUMN clan_broadcast_settings.level_every_five_minimum IS 'Native setting 67; zero disables.';
COMMENT ON COLUMN clan_broadcast_settings.level_every_one_minimum IS 'Native setting 68; zero disables.';
