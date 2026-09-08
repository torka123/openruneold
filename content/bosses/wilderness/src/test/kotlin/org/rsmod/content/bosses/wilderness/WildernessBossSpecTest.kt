package org.rsmod.content.bosses.wilderness

import dev.openrune.gamevals.GameValProvider
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.rsmod.api.bosses.spec.Condition
import org.rsmod.api.bosses.spec.DamageExpr
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.bosses.spec.HitType
import org.rsmod.api.bosses.spec.ProtectionPrayer
import org.rsmod.api.bosses.spec.Selector
import org.rsmod.map.CoordGrid

class WildernessBossSpecTest {
    @Test
    fun `documented attack intervals and forms are encoded`() {
        assertEquals(4, CHAOS_ELEMENTAL_SPEC.stats.attackRate)
        assertEquals(5, MALEDICTUS_SPEC.stats.attackRate)
        assertEquals(4, CALLISTO_SPEC.stats.attackRate)
        assertEquals(4, ARTIO_SPEC.stats.attackRate)
        assertEquals(4, VENENATIS_SPEC.stats.attackRate)
        assertEquals(4, SPINDEL_SPEC.stats.attackRate)
        assertEquals(6, VETION_SPEC.phases.getValue("normal").attackRate)
        assertEquals(5, VETION_SPEC.phases.getValue("enraged").attackRate)
        assertEquals(6, CALVARION_SPEC.phases.getValue("normal").attackRate)
        assertEquals(5, CALVARION_SPEC.phases.getValue("enraged").attackRate)
    }

    @Test
    fun `maledictus cycles ranged spikes and the three telegraphed specials`() {
        val selector =
            assertIs<Selector.Rotation>(MALEDICTUS_SPEC.phases.getValue("combat").selector)
        assertEquals(
            listOf(
                "ranged",
                "ranged",
                "ranged",
                "ice",
                "ranged",
                "ranged",
                "ranged",
                "blood",
                "ranged",
                "ranged",
                "ranged",
                "air",
            ),
            selector.sequence,
        )
        assertTrue("magic" !in MALEDICTUS_SPEC.abilities)
    }

    @Test
    fun `every directly executed combat asset resolves in revision 240`() {
        val root =
            generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
                .first { Files.isRegularFile(it.resolve("settings.gradle.kts")) }
        val mappings = GameValProvider.loadIsolated(root.toString() + File.separator).mappings
        val keys =
            WILDERNESS_RUNTIME_SEQUENCE_KEYS +
                WILDERNESS_RUNTIME_SPOTANIM_KEYS +
                WILDERNESS_RUNTIME_LOC_KEYS +
                WILDERNESS_RUNTIME_NPC_KEYS
        val missing = keys.filter { key -> mappings[key.substringBefore('.')]?.get(key) == null }

        val related =
            mappings["seq"]
                ?.filterKeys { it.contains("callisto", ignoreCase = true) }
                ?.toSortedMap()
                .orEmpty()
        assertTrue(
            missing.isEmpty(),
            "Missing revision-240 combat assets: $missing; Callisto sequences: $related",
        )
    }

    @Test
    fun `all requested specs have combat abilities and phases`() {
        val specs =
            listOf(
                CHAOS_ELEMENTAL_SPEC,
                MALEDICTUS_SPEC,
                CALLISTO_SPEC,
                ARTIO_SPEC,
                VENENATIS_SPEC,
                SPINDEL_SPEC,
                VETION_SPEC,
                CALVARION_SPEC,
            )
        assertTrue(specs.all { it.abilities.isNotEmpty() && it.phases.isNotEmpty() })
        assertEquals(listOf("npc.vetion", "npc.vetion_2"), VETION_SPEC.npcTypes)
        assertEquals(listOf("npc.vetion_single", "npc.vetion_2_single"), CALVARION_SPEC.npcTypes)
    }

    @Test
    fun `bear variants acquire players and chase until their cave exit`() {
        val root =
            generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
                .first { Files.isRegularFile(it.resolve("settings.gradle.kts")) }
        val config = Files.readString(root.resolve(".data/raw-cache/server/npcs.toml"))
        listOf("npc.callisto", "npc.callisto_singles").forEach { id ->
            val block = config.substringAfter("id = \"$id\"").substringBefore("[[npc]]")
            assertTrue(
                Regex("(?m)^huntMode\\s*=\\s*6\\s*$").containsMatchIn(block),
                "$id must use aggressive player hunting.",
            )
            assertTrue(
                Regex("(?m)^huntRange\\s*=\\s*12\\s*$").containsMatchIn(block),
                "$id must acquire players throughout its combat area.",
            )
            assertTrue(
                Regex("(?m)^maxRange\\s*=\\s*48\\s*$").containsMatchIn(block),
                "$id must retain its target across every tile in its cave.",
            )
        }

        val callistoSpawn = CoordGrid(3358, 10320)
        val artioSpawn = CoordGrid(1759, 11536)
        assertTrue(bearCaveChaseAllowed("npc.callisto", callistoSpawn, CoordGrid(3391, 10367)))
        assertFalse(bearCaveChaseAllowed("npc.callisto", callistoSpawn, CoordGrid(3358, 10299)))
        assertTrue(bearCaveChaseAllowed("npc.callisto_singles", artioSpawn, CoordGrid(1791, 11583)))
        assertFalse(
            bearCaveChaseAllowed("npc.callisto_singles", artioSpawn, CoordGrid(1759, 11519))
        )

        // Clan Mayhem can reuse Callisto outside the normal den without inheriting this boundary.
        assertTrue(
            bearCaveChaseAllowed("npc.callisto", CoordGrid(3200, 10000), CoordGrid(3210, 10010))
        )
    }

    @Test
    fun `Vetion aggressively pursues players between attacks`() {
        val root =
            generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
                .first { Files.isRegularFile(it.resolve("settings.gradle.kts")) }
        val config = Files.readString(root.resolve(".data/raw-cache/server/npcs.toml"))
        val block = config.substringAfter("id = \"npc.vetion\"").substringBefore("[[npc]]")
        mapOf(
                "huntMode" to 6,
                "huntRange" to 12,
                "attackRange" to 1,
                "wanderRange" to 8,
                "respawnRate" to 30,
            )
            .forEach { (field, expected) ->
                assertTrue(
                    Regex("(?m)^$field\\s*=\\s*$expected\\s*$").containsMatchIn(block),
                    "Vet'ion must set $field=$expected in the active server cache override.",
                )
            }
        assertTrue(Regex("(?m)^\"param\\.poison_immunity\"\\s*=\\s*1\\s*$").containsMatchIn(block))
        assertTrue(Regex("(?m)^\"param\\.venom_immunity\"\\s*=\\s*2\\s*$").containsMatchIn(block))
    }

    @Test
    fun `bear profile names match prefixed cache npc internal names`() {
        assertEquals("callisto", normalizeNpcInternalName("npc.callisto"))
        assertEquals("callisto_singles", normalizeNpcInternalName("npc.callisto_singles"))
        assertEquals("callisto_singles", normalizeNpcInternalName("callisto_singles"))
    }

    @Test
    fun `bear attacks never queue a zero tick player hit`() {
        val root =
            generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
                .first { Files.isRegularFile(it.resolve("settings.gradle.kts")) }
        val source =
            Files.readString(
                root.resolve(
                    "content/bosses/wilderness/src/main/kotlin/org/rsmod/content/bosses/wilderness/WildernessBossCombat.kt"
                )
            )
        val bearCombat =
            source
                .substringAfter("private fun performBearMelee(")
                .substringBefore("private fun performSpiderAttack(")

        assertTrue(BEAR_HIT_QUEUE_DELAY > 0)
        assertFalse(
            Regex("""player\.queueHit\(\s*npc,\s*0,""").containsMatchIn(bearCombat),
            "Bear attacks must never pass a zero-cycle delay to Player.queueHit.",
        )
    }

    @Test
    fun `venenatis variants use their OSRS aggression and respawn configuration`() {
        val root =
            generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
                .first { Files.isRegularFile(it.resolve("settings.gradle.kts")) }
        val config = Files.readString(root.resolve(".data/raw-cache/server/npcs.toml"))
        mapOf("npc.venenatis" to 30, "npc.venenatis_singles" to 16).forEach { (id, range) ->
            val block = config.substringAfter("id = \"$id\"").substringBefore("[[npc]]")
            mapOf(
                    "huntMode" to 6,
                    "wanderRange" to 0,
                    "respawnRate" to 20,
                    "attackRange" to 10,
                    "huntRange" to range,
                )
                .forEach { (field, expected) ->
                    assertTrue(
                        Regex("(?m)^$field\\s*=\\s*$expected\\s*$").containsMatchIn(block),
                        "$id must set $field=$expected in the active cache override.",
                    )
                }
            assertTrue(
                Regex("(?m)^\"param\\.poison_immunity\"\\s*=\\s*1\\s*$").containsMatchIn(block)
            )
            assertTrue(
                Regex("(?m)^\"param\\.venom_immunity\"\\s*=\\s*2\\s*$").containsMatchIn(block)
            )
        }
    }

    @Test
    fun `chaos elemental discord is a ranged projectile with a hidden random damage style`() {
        val expected =
            mapOf(
                "discord_magic" to HitType.Magic,
                "discord_ranged" to HitType.Ranged,
                "discord_melee" to HitType.Melee,
            )
        expected.forEach { (ability, type) ->
            val effect = CHAOS_ELEMENTAL_SPEC.abilities.getValue(ability)
            val projectile = effect.projectiles().single()
            val hit = requireNotNull(projectile.hit)
            assertEquals(type, hit.type)
            assertEquals(0..CHAOS_ELEMENTAL_MAX_HIT, assertIs<DamageExpr.Roll>(hit.damage).range)
            assertEquals(ProtectionPrayer.Standard, hit.protectionPrayer)
            assertEquals("spotanim.chaoselemental_spotanim_discord_travel", projectile.spotanim)
            assertEquals("spotanim.chaoselemental_spotanim_discord_impact", projectile.impact)
            assertTrue(
                effect.spotanims().contains("spotanim.chaoselemental_spotanim_discord_casting")
            )
        }

        val selector =
            assertIs<Selector.WeightedRandom>(
                CHAOS_ELEMENTAL_SPEC.phases.getValue("combat").selector
            )
        assertEquals(3, selector.entries.single { it.ability == "discord_magic" }.weight)
        assertEquals(2, selector.entries.single { it.ability == "discord_ranged" }.weight)
        assertEquals(1, selector.entries.single { it.ability == "discord_melee" }.weight)
        assertEquals(
            Condition.Always,
            selector.entries.single { it.ability == "discord_melee" }.requires,
        )
        assertTrue(selector.entries.any { it.ability == "confusion" })
        assertTrue(selector.entries.any { it.ability == "madness" })
    }

    @Test
    fun `bear variants use one encounter-scoped attack entry point`() {
        listOf(CALLISTO_SPEC, ARTIO_SPEC).forEach { spec ->
            assertEquals(setOf("attack"), spec.abilities.keys)
            val selector = assertIs<Selector.Rotation>(spec.phases.getValue("combat").selector)
            assertEquals(listOf("attack"), selector.sequence)
            assertEquals(BEAR_ATTACK_RATE_TICKS, spec.stats.attackRate)
        }
    }

    @Test
    fun `bear thresholds include exact hits crossings and double crossings once`() {
        val callisto = listOf(666, 333)
        assertEquals(listOf(666), bearThresholdsCrossed(700, 666, callisto))
        assertEquals(listOf(666), bearThresholdsCrossed(700, 650, callisto))
        assertEquals(listOf(666, 333), bearThresholdsCrossed(1000, 300, callisto))
        assertEquals(listOf(333), bearThresholdsCrossed(650, 300, callisto, fired = setOf(666)))
        assertTrue(bearThresholdsCrossed(666, 600, callisto, fired = setOf(666)).isEmpty())

        val artio = listOf(300, 150)
        assertEquals(listOf(300), bearThresholdsCrossed(301, 300, artio))
        assertEquals(listOf(300, 150), bearThresholdsCrossed(450, 149, artio))
    }

    @Test
    fun `bear knockback damage scales with blocked movement`() {
        assertEquals(50, bearKnockbackDamage(0))
        assertEquals(35, bearKnockbackDamage(1))
        assertEquals(20, bearKnockbackDamage(2))
        assertEquals(5, bearKnockbackDamage(3))
    }

    @Test
    fun `bear bind durations and lifecycle timing are explicit`() {
        assertEquals(8, bearFreezeTicks("Bind"))
        assertEquals(8, bearFreezeTicks("Ice Rush"))
        assertEquals(16, bearFreezeTicks("Snare"))
        assertEquals(16, bearFreezeTicks("Ice Burst"))
        assertEquals(24, bearFreezeTicks("Entangle"))
        assertEquals(24, bearFreezeTicks("Ice Blitz"))
        assertEquals(32, bearFreezeTicks("Ice Barrage"))
        assertEquals(0, bearFreezeTicks("Fire Surge"))
        assertEquals(3, BEAR_SPAWN_LOCK_TICKS)
        assertEquals(4, BEAR_ROAR_LOCK_TICKS)
        assertEquals(5, BEAR_NATURAL_FREEZE_IMMUNITY_TICKS)
        assertEquals(15, BEAR_TRAP_MAX_HIT)
        assertEquals(3, BEAR_KNOCKBACK_DISTANCE)
        assertEquals(1..4, BEAR_MAGIC_MIN_NORMAL_ATTACKS..BEAR_MAGIC_MAX_NORMAL_ATTACKS)
    }

    @Test
    fun `spider variants use the exact eight ranged eight magic cycle with melee override`() {
        listOf(VENENATIS_SPEC, SPINDEL_SPEC).forEach { spec ->
            val selector =
                assertIs<Selector.MeleeOverrideRotation>(spec.phases.getValue("combat").selector)
            assertEquals("melee", selector.meleeAbility)
            assertEquals(16, selector.sequence.size)
            assertEquals(List(8) { "ranged" }, selector.sequence.subList(0, 8))
            assertEquals(List(8) { "magic" }, selector.sequence.subList(8, 16))
        }
    }

    @Test
    fun `spider variants retain their style specific max hits`() {
        assertEquals("true|21|35|30", VENENATIS_SPEC.abilities.getValue("melee").externalParam())
        assertEquals("false|14|31|24", SPINDEL_SPEC.abilities.getValue("melee").externalParam())
    }

    @Test
    fun `spider specials and movement are tied to committed attack opportunities`() {
        assertEquals(SpiderSpecial.Spiderlings, spiderSpecialAt(0))
        assertEquals(SpiderSpecial.Web, spiderSpecialAt(10))
        assertEquals(null, spiderSpecialAt(1))
        assertEquals(listOf(3, 7, 11, 15), (0 until 16).filter { spiderShouldMove(it) })
        assertEquals(HitType.Ranged, spiderDistantStyle(0))
        assertEquals(HitType.Ranged, spiderDistantStyle(7))
        assertEquals(HitType.Magic, spiderDistantStyle(8))
        assertEquals(HitType.Magic, spiderDistantStyle(15))
        assertEquals(HitType.Ranged, spiderDistantStyle(16))
    }

    @Test
    fun `venenatis current crowd threshold starts above ten players`() {
        assertEquals(2, venenatisSpiderlingCount(1))
        assertEquals(2, venenatisSpiderlingCount(2))
        assertEquals(2, venenatisSpiderlingCount(10))
        assertEquals(3, venenatisSpiderlingCount(11))
        assertEquals(
            (1 until 16 step 2).toList(),
            (0 until 16).filter { spiderShouldMove(it, true) },
        )

        var lastSpecial: SpiderSpecial? = null
        val crowdedSpecials =
            (0 until 16).mapNotNull { opportunity ->
                spiderSpecialAt(opportunity, crowded = true, lastSpecial = lastSpecial)
                    ?.also { lastSpecial = it }
                    ?.let { opportunity to it }
            }
        assertEquals(
            listOf(
                0 to SpiderSpecial.Spiderlings,
                1 to SpiderSpecial.Web,
                3 to SpiderSpecial.Spiderlings,
                5 to SpiderSpecial.Web,
                7 to SpiderSpecial.Spiderlings,
                9 to SpiderSpecial.Web,
                11 to SpiderSpecial.Spiderlings,
                13 to SpiderSpecial.Web,
                15 to SpiderSpecial.Spiderlings,
            ),
            crowdedSpecials,
        )
        assertEquals(null, spiderSpecialAt(16, crowded = true, lastSpecial = lastSpecial))
        assertEquals(
            SpiderSpecial.Web,
            spiderSpecialAt(17, crowded = true, lastSpecial = lastSpecial),
        )
    }

    @Test
    fun `spiderlings always die in one player attack and empower Venenatis up to thirty`() {
        assertEquals(5, spiderlingForcedDamage(5))
        assertEquals(0, spiderlingForcedDamage(0))
        assertEquals(0, spiderlingEmpoweredMaximum(0))
        assertEquals(10, spiderlingEmpoweredMaximum(1))
        assertEquals(20, spiderlingEmpoweredMaximum(2))
        assertEquals(30, spiderlingEmpoweredMaximum(3))
        assertEquals(30, spiderlingEmpoweredMaximum(20))
    }

    @Test
    fun `undead lifecycle owns two correct hounds per form`() {
        assertEquals("npc.vetion_hellhound_jnr", VETION_PROFILE.normalHound)
        assertEquals("npc.vetion_hellhound_snr", VETION_PROFILE.enragedHound)
        assertEquals("npc.vetion_hellhound_jnr_singles", CALVARION_PROFILE.normalHound)
        assertEquals("npc.vetion_hellhound_snr_singles", CALVARION_PROFILE.enragedHound)
        assertEquals(HOUNDS_PER_WAVE, 2)
        assertEquals(4, HOUND_ATTACK_RATE)
        assertEquals(500, VETION_PROFILE.enragedTimeoutTicks)
        assertEquals(null, CALVARION_PROFILE.enragedTimeoutTicks)
        assertEquals(2, UNDEAD_SPAWN_LOCK_TICKS)
        assertEquals(2, UNDEAD_TRANSFORM_TICKS)
        assertEquals(5, UNDEAD_DEATH_ANIM_TICKS)
    }

    @Test
    fun `undead threshold clipping cannot skip either hound gate`() {
        assertEquals(127, undeadHoundThreshold(255))
        assertEquals(75, undeadHoundThreshold(150))
        assertEquals(73, undeadDamageToThreshold(current = 200, incoming = 500, threshold = 127))
        assertEquals(0, undeadDamageToThreshold(current = 127, incoming = 99, threshold = 127))
    }

    @Test
    fun `active undead forms repair a stale damage gate before hit modification`() {
        assertTrue(
            shouldUnlockUndeadDamage(
                lifecycle = UndeadLifecycleState.FORM_ONE_ACTIVE,
                active = true,
                slotAssigned = true,
                hitpoints = 255,
                livingHounds = 0,
            )
        )
        assertTrue(
            shouldUnlockUndeadDamage(
                lifecycle = UndeadLifecycleState.FORM_TWO_ACTIVE,
                active = true,
                slotAssigned = true,
                hitpoints = 255,
                livingHounds = 0,
            )
        )
    }

    @Test
    fun `protected undead phases never repair their damage gate`() {
        val protected =
            setOf(
                UndeadLifecycleState.DESPAWNED,
                UndeadLifecycleState.SPAWNING,
                UndeadLifecycleState.FORM_ONE_HOUNDS,
                UndeadLifecycleState.TRANSFORMING,
                UndeadLifecycleState.FORM_TWO_HOUNDS,
                UndeadLifecycleState.FINAL_DEATH,
                UndeadLifecycleState.RESPAWN_PENDING,
                UndeadLifecycleState.RESETTING,
            )
        protected.forEach { lifecycle ->
            assertFalse(
                shouldUnlockUndeadDamage(
                    lifecycle = lifecycle,
                    active = true,
                    slotAssigned = true,
                    hitpoints = 255,
                    livingHounds = 0,
                ),
                "Damage gate unexpectedly unlocked during $lifecycle",
            )
        }
        assertFalse(
            shouldUnlockUndeadDamage(
                lifecycle = UndeadLifecycleState.FORM_ONE_ACTIVE,
                active = true,
                slotAssigned = true,
                hitpoints = 255,
                livingHounds = 1,
            )
        )
    }

    @Test
    fun `active bears repair a stale damage gate before hit modification`() {
        assertTrue(
            shouldUnlockBearDamage(
                lifecycle = BearLifecycleState.ACTIVE,
                active = true,
                slotAssigned = true,
                hitpoints = 1_000,
            )
        )
    }

    @Test
    fun `bear spawn grace retains targets but prevents attacks until active`() {
        assertTrue(BearLifecycleState.SPAWNING.allowsTargetAcquisition)
        assertFalse(BearLifecycleState.SPAWNING.allowsAttacks)
        assertTrue(BearLifecycleState.ACTIVE.allowsTargetAcquisition)
        assertTrue(BearLifecycleState.ACTIVE.allowsAttacks)
        assertFalse(BearLifecycleState.UNINITIALIZED.allowsTargetAcquisition)
        assertFalse(BearLifecycleState.UNINITIALIZED.allowsAttacks)
        assertFalse(BearLifecycleState.DEAD.allowsTargetAcquisition)
        assertFalse(BearLifecycleState.DEAD.allowsAttacks)
    }

    @Test
    fun `noncombat bear lifecycle states keep their damage gate`() {
        setOf(
                BearLifecycleState.UNINITIALIZED,
                BearLifecycleState.SPAWNING,
                BearLifecycleState.DEAD,
            )
            .forEach { lifecycle ->
                assertFalse(
                    shouldUnlockBearDamage(
                        lifecycle = lifecycle,
                        active = true,
                        slotAssigned = true,
                        hitpoints = 1_000,
                    ),
                    "Damage gate unexpectedly unlocked during $lifecycle",
                )
            }
        assertFalse(
            shouldUnlockBearDamage(
                lifecycle = BearLifecycleState.ACTIVE,
                active = false,
                slotAssigned = true,
                hitpoints = 1_000,
            )
        )
    }

    @Test
    fun `undead lifecycle only permits attacks in live combat forms`() {
        assertEquals(
            setOf(
                UndeadLifecycleState.FORM_ONE_ACTIVE,
                UndeadLifecycleState.FORM_ONE_HOUNDS,
                UndeadLifecycleState.FORM_TWO_ACTIVE,
                UndeadLifecycleState.FORM_TWO_HOUNDS,
            ),
            UndeadLifecycleState.entries.filter { it.allowsAttacks }.toSet(),
        )
    }

    @Test
    fun `initial and overdue bear spawns recover but live and dead bears do not`() {
        assertTrue(
            shouldRecoverBearLifecycle(
                BearLifecycleState.UNINITIALIZED,
                active = false,
                slotAssigned = true,
                hitpoints = 1_000,
                currentTick = 50,
                spawnUnlockTick = Int.MIN_VALUE,
            )
        )
        assertTrue(
            shouldRecoverBearLifecycle(
                BearLifecycleState.SPAWNING,
                active = true,
                slotAssigned = true,
                hitpoints = 1_000,
                currentTick = 53,
                spawnUnlockTick = 53,
            )
        )
        assertFalse(
            shouldRecoverBearLifecycle(
                BearLifecycleState.SPAWNING,
                active = true,
                slotAssigned = true,
                hitpoints = 1_000,
                currentTick = 52,
                spawnUnlockTick = 53,
            )
        )
        assertFalse(
            shouldRecoverBearLifecycle(
                BearLifecycleState.DEAD,
                active = false,
                slotAssigned = true,
                hitpoints = 1,
                currentTick = 60,
                spawnUnlockTick = Int.MIN_VALUE,
            )
        )
    }

    @Test
    fun `initial and overdue undead spawns recover without bypassing hound immunity`() {
        assertTrue(
            shouldRecoverUndeadLifecycle(
                UndeadLifecycleState.DESPAWNED,
                active = false,
                slotAssigned = true,
                hitpoints = 255,
                currentTick = 50,
                spawnUnlockTick = Int.MIN_VALUE,
            )
        )
        assertTrue(
            shouldRecoverUndeadLifecycle(
                UndeadLifecycleState.SPAWNING,
                active = true,
                slotAssigned = true,
                hitpoints = 255,
                currentTick = 52,
                spawnUnlockTick = 52,
            )
        )
        assertFalse(
            shouldRecoverUndeadLifecycle(
                UndeadLifecycleState.FORM_ONE_HOUNDS,
                active = true,
                slotAssigned = true,
                hitpoints = 127,
                currentTick = 60,
                spawnUnlockTick = Int.MIN_VALUE,
            )
        )
        assertFalse(
            shouldRecoverUndeadLifecycle(
                UndeadLifecycleState.FINAL_DEATH,
                active = false,
                slotAssigned = true,
                hitpoints = 1,
                currentTick = 60,
                spawnUnlockTick = Int.MIN_VALUE,
            )
        )
    }

    @Test
    fun `undead bosses lead with four lightning attacks before an eligible shield bash`() {
        listOf(VETION_SPEC, CALVARION_SPEC).forEach { spec ->
            listOf("normal", "enraged").forEach { phaseName ->
                val selector = assertIs<Selector.Rotation>(spec.phases.getValue(phaseName).selector)
                assertEquals(
                    listOf("lightning", "lightning", "lightning", "lightning", "shield_bash"),
                    selector.sequence,
                )
            }
        }
    }

    @Test
    fun `undead hazards encode capped 3x3 lightning and attack only bash lock`() {
        assertEquals(4, RANDOM_LIGHTNING_COUNT)
        assertEquals(3, MAX_TARGETED_LIGHTNING)
        assertEquals(7, MAX_LIGHTNING_COUNT)
        assertEquals(4, undeadLightningStrikeCount(playerCount = 0))
        assertEquals(5, undeadLightningStrikeCount(playerCount = 1))
        assertEquals(6, undeadLightningStrikeCount(playerCount = 2))
        assertEquals(7, undeadLightningStrikeCount(playerCount = 3))
        assertEquals(7, undeadLightningStrikeCount(playerCount = 20))
        assertEquals(2, LIGHTNING_WINDUP_TICKS)
        assertEquals(2, BASH_WINDUP_TICKS)
        assertEquals(8, BASH_ATTACK_LOCK_TICKS)
        assertEquals(2, BASH_RADIUS)
        assertEquals(44, undeadLightningDamageMaximum(44, distance = 0))
        assertEquals(22, undeadLightningDamageMaximum(44, distance = 1))
        assertEquals(13, undeadLightningDamageMaximum(26, distance = 1))
        assertTrue(undeadDirectTargetEligible(targetValid = true, sameLevel = true, distance = 0))
        assertTrue(undeadDirectTargetEligible(targetValid = true, sameLevel = true, distance = 16))
        assertFalse(undeadDirectTargetEligible(targetValid = true, sameLevel = true, distance = 17))
        assertFalse(undeadDirectTargetEligible(targetValid = true, sameLevel = false, distance = 1))
        assertFalse(undeadDirectTargetEligible(targetValid = false, sameLevel = true, distance = 1))
        val origin = org.rsmod.map.CoordGrid(3200, 3200, 0)
        val bashTiles = undeadBashTiles(origin, npcSize = 3)
        assertEquals(25, bashTiles.size)
        assertTrue(origin.translate(1, 1) in bashTiles)
        val transformationTiles = undeadTransformationStrikeTiles(origin, npcSize = 3)
        assertEquals(8, transformationTiles.size)
        assertTrue(origin.translate(1, 1) !in transformationTiles)
        assertEquals(44, VETION_SPEC.abilities.getValue("lightning").externalParam())
        assertEquals(35, VETION_SPEC.abilities.getValue("shield_bash").externalParam())
        assertEquals(26, CALVARION_SPEC.abilities.getValue("lightning").externalParam())
        assertEquals(26, CALVARION_SPEC.abilities.getValue("shield_bash").externalParam())
        assertTrue(VETION_SPEC.abilities.getValue("shield_bash").hits().isEmpty())
        assertTrue(VETION_SPEC.abilities.getValue("lightning").hits().isEmpty())
        assertTrue("seq.wbr_vention_hellhound_death" in WILDERNESS_RUNTIME_SEQUENCE_KEYS)
    }

    private fun Effect.externalParam(): Any =
        when (this) {
            is Effect.External -> requireNotNull(params)
            is Effect.Sequence ->
                requireNotNull(effects.filterIsInstance<Effect.External>().single().params)
            else -> error("No external parameter in $this")
        }

    private fun Effect.hits(): List<Effect.Hit> =
        when (this) {
            is Effect.Hit -> listOf(this)
            is Effect.Projectile -> listOfNotNull(hit)
            is Effect.Sequence -> effects.flatMap { it.hits() }
            else -> emptyList()
        }

    private fun Effect.projectiles(): List<Effect.Projectile> =
        when (this) {
            is Effect.Projectile -> listOf(this)
            is Effect.Sequence -> effects.flatMap { it.projectiles() }
            else -> emptyList()
        }

    private fun Effect.spotanims(): List<String> =
        when (this) {
            is Effect.Spotanim -> listOf(spot)
            is Effect.Sequence -> effects.flatMap { it.spotanims() }
            else -> emptyList()
        }
}
