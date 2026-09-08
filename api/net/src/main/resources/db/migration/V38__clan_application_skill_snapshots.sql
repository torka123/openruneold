-- Native applicants script 4375 renders an immutable application-time stat snapshot.
-- Nullable columns preserve pre-migration applications as explicit unknown/--- values.
ALTER TABLE clan_applications
    ADD COLUMN IF NOT EXISTS combat_level SMALLINT CHECK (combat_level BETWEEN 0 AND 126),
    ADD COLUMN IF NOT EXISTS total_level SMALLINT CHECK (total_level BETWEEN 0 AND 3000),
    ADD COLUMN IF NOT EXISTS quest_points SMALLINT CHECK (quest_points >= 0),
    ADD COLUMN IF NOT EXISTS is_iron BOOLEAN,
    ADD COLUMN IF NOT EXISTS is_ultimate_iron BOOLEAN,
    ADD COLUMN IF NOT EXISTS is_hardcore_iron BOOLEAN,
    ADD COLUMN IF NOT EXISTS skill_levels VARCHAR(95);
