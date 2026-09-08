package org.rsmod.content.drops.tables.monsters

/*
 * Doom of Mokhaiotl deliberately has no globally registered NPC drop table.
 *
 * Its rewards accumulate across delves, can be risked by descending, and are claimed from the
 * burrow/lobby chest. The authoritative weighted table therefore lives in DoomRewardService and
 * DoomCombat uses deathNoDrops. Registering a normal NPC table here would create a second loot
 * authority and allow direct or duplicate drops.
 */
