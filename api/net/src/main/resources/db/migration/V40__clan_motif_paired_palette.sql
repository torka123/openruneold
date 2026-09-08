UPDATE clans
SET motif_secondary_colour = motif_primary_colour
WHERE motif_secondary_colour <> motif_primary_colour;

ALTER TABLE clans
    DROP CONSTRAINT IF EXISTS clans_motif_paired_palette_check;

ALTER TABLE clans
    ADD CONSTRAINT clans_motif_paired_palette_check
    CHECK (motif_secondary_colour = motif_primary_colour);
