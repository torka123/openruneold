package org.rsmod.content.skills.magic.spell.attacks.arceuus

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import kotlin.math.min
import org.rsmod.api.combat.commons.CombatCorruption
import org.rsmod.api.combat.commons.magic.ArceuusSpellState
import org.rsmod.api.combat.commons.magic.MagicSpell
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.combat.manager.MagicRuneManager.Companion.isFailure
import org.rsmod.api.player.output.UpdateRun
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.righthand
import org.rsmod.api.player.stat.magicLvl
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.api.table.herblore.HerbloreCleaningRow
import org.rsmod.api.table.prayer.SkillPrayerRow
import org.rsmod.game.MapClock
import org.rsmod.game.hit.HitType
import org.rsmod.game.inv.isType
import org.rsmod.events.EventBus
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Non-target Arceuus spells, driven by revision-240 spell objects and rune requirements. */
internal class ArceuusUtilitySpellScript
@Inject
constructor(
    private val spells: MagicSpellRegistry,
    private val runes: MagicRuneManager,
    private val state: ArceuusUtilityState,
    private val corruption: CombatCorruption,
    private val clock: MapClock,
    private val eventBus: EventBus,
    private val protectedAccess: ProtectedAccessLauncher,
    private val world: WorldRepository,
) : PluginScript() {
    private val cleaningRows by lazy { HerbloreCleaningRow.all().associateBy { it.input.id } }
    private val prayerRows by lazy { SkillPrayerRow.all().associateBy { it.item.id } }

    override fun ScriptContext.startup() {
        for (definition in ArceuusUtilitySpell.entries) {
            val spell = definition.resolveSpell() ?: continue
            onIfOverlayButton(spell.component) { cast(spell, definition) }
        }
        for (definition in ArceuusThralls.definitions) {
            val spell = definition.resolveSpell() ?: continue
            onIfOverlayButton(spell.component) { castThrall(spell, definition) }
        }
        onPlayerLogout { state.clear(player) }
    }

    private fun ArceuusUtilitySpell.resolveSpell(): MagicSpell? = resolveSpell(spellObj)

    private fun ArceuusThrallDefinition.resolveSpell(): MagicSpell? = resolveSpell(spellObj)

    private fun resolveSpell(internal: String): MagicSpell? {
        val type = ServerCacheManager.getItem(internal.asRSCM(RSCMType.OBJ)) ?: return null
        return spells.getObjSpell(type)
    }


    private suspend fun ProtectedAccess.cast(spell: MagicSpell, definition: ArceuusUtilitySpell) {
        if (actionDelay > mapClock || !prevalidate(definition)) return
        if (runes.attemptCast(player, spell).isFailure()) return

        when (definition) {
            ArceuusUtilitySpell.MarkOfDarkness -> castMark(spell)
            ArceuusUtilitySpell.LesserCorruption -> castCorruption(spell, greater = false)
            ArceuusUtilitySpell.GreaterCorruption -> castCorruption(spell, greater = true)
            ArceuusUtilitySpell.ShadowVeil -> castShadowVeil(spell)
            ArceuusUtilitySpell.VileVigour -> castVileVigour(spell)
            ArceuusUtilitySpell.Degrime -> castDegrime(spell)
            ArceuusUtilitySpell.DeathCharge -> castDeathCharge(spell)
            ArceuusUtilitySpell.WardOfArceuus -> castWard(spell)
            ArceuusUtilitySpell.DemonicOffering -> castOffering(spell, ashes = true)
            ArceuusUtilitySpell.SinisterOffering -> castOffering(spell, ashes = false)
        }
    }

    private fun ProtectedAccess.prevalidate(definition: ArceuusUtilitySpell): Boolean =
        when (definition) {
            ArceuusUtilitySpell.LesserCorruption,
            ArceuusUtilitySpell.GreaterCorruption ->
                requireReady(
                    ArceuusCooldown.Corruption,
                    "You can only cast corruption spells every 30 seconds.",
                )
            ArceuusUtilitySpell.DeathCharge -> {
                when {
                    state.hasDeathCharge(player) -> {
                        mes("You already have a Death Charge pending on you.")
                        false
                    }
                    else -> requireReady(ArceuusCooldown.DeathCharge, "Death Charge is still on cooldown.")
                }
            }
            ArceuusUtilitySpell.ShadowVeil ->
                requireReady(
                    ArceuusCooldown.ShadowVeil,
                    "You can only cast Shadow Veil every 30 seconds.",
                )
            ArceuusUtilitySpell.VileVigour -> {
                when {
                    !requireReady(
                        ArceuusCooldown.VileVigour,
                        "You can only cast Vile Vigour every 10 seconds.",
                    ) -> false
                    player.runEnergy >= MAX_RUN_ENERGY -> {
                        mes("You're already at maximum run energy.")
                        false
                    }
                    player.stat(PRAYER_STAT) <= 0 -> {
                        mes("You don't have enough Prayer points to cast that spell.")
                        false
                    }
                    else -> true
                }
            }
            ArceuusUtilitySpell.Degrime -> {
                if (eligibleHerbSlots().isEmpty()) {
                    mes("You don't have any suitable herbs to clean.")
                    false
                } else {
                    true
                }
            }
            ArceuusUtilitySpell.WardOfArceuus ->
                requireReady(
                    ArceuusCooldown.Ward,
                    "You can only cast Ward of Arceuus every 30 seconds.",
                )
            ArceuusUtilitySpell.DemonicOffering ->
                requireOffering(ashes = true, "You aren't holding any demonic ashes.")
            ArceuusUtilitySpell.SinisterOffering ->
                requireOffering(ashes = false, "You aren't holding any suitable bones.")
            ArceuusUtilitySpell.MarkOfDarkness -> true
        }

    private fun ProtectedAccess.requireReady(type: ArceuusCooldown, message: String): Boolean {
        if (!state.onCooldown(player, type)) return true
        mes(message)
        return false
    }

    private fun ProtectedAccess.requireOffering(ashes: Boolean, missingMessage: String): Boolean {
        if (!requireReady(ArceuusCooldown.Offering, "You can only cast offering spells every 5 seconds.")) {
            return false
        }
        if (eligibleOfferingSlots(ashes).isNotEmpty()) return true
        mes(missingMessage)
        return false
    }

    private fun ProtectedAccess.castMark(spell: MagicSpell) {
        play(8970, 1852, 5046)
        val multiplier = if (player.righthand.isType("obj.purging_staff")) 5 else 1
        ArceuusSpellState.mark(player, clock.cycle + player.statBase(MAGIC_STAT) * 3 * multiplier)
        mes("<col=a53fff>You have placed a Mark of Darkness upon yourself.</col>")
        finish(spell)
    }

    private fun ProtectedAccess.castCorruption(spell: MagicSpell, greater: Boolean) {
        play(8977, if (greater) 1878 else 1877, if (greater) 5045 else 5037)
        state.armCorruption(player, greater)
        state.setCooldown(player, ArceuusCooldown.Corruption, CORRUPTION_COOLDOWN)
        finish(spell)
    }

    private fun ProtectedAccess.castShadowVeil(spell: MagicSpell) {
        play(8979, 1881, 5032)
        state.setCooldown(player, ArceuusCooldown.ShadowVeil, SHADOW_VEIL_COOLDOWN)
        ArceuusSpellState.shadowVeil(player, clock.cycle + player.statBase(MAGIC_STAT))
        mes("<col=a53fff>Your thieving abilities have been enhanced.</col>")
        finish(spell)
    }

    private fun ProtectedAccess.castVileVigour(spell: MagicSpell) {
        val prayer = player.stat(PRAYER_STAT)
        val missingPercent = (MAX_RUN_ENERGY - player.runEnergy + RUN_ENERGY_PER_PRAYER - 1) / RUN_ENERGY_PER_PRAYER
        val converted = min(prayer, missingPercent)
        player.statSub(PRAYER_STAT, converted, percent = 0)
        player.runEnergy = min(MAX_RUN_ENERGY, player.runEnergy + converted * RUN_ENERGY_PER_PRAYER)
        UpdateRun.energy(player, player.runEnergy)
        play(8978, 1876, 5049)
        state.setCooldown(player, ArceuusCooldown.VileVigour, VILE_VIGOUR_COOLDOWN)
        finish(spell)
    }

    private suspend fun ProtectedAccess.castDegrime(spell: MagicSpell) {
        actionDelay = mapClock + DEGRIME_ACTION_DELAY
        player.anim(seq(8981), priority = 6)
        delay(4)
        player.anim(seq(8980), priority = 6)
        spotanim(spot(1885))
        soundSynth(synth(5052))
        delay(2)
        for ((slot, row) in eligibleHerbSlots()) {
            val current = inv[slot] ?: continue
            if (current.id != row.input.id) continue
            if (invReplaceSlot(inv, slot, 1, row.output).success && row.xp > 0) {
                statAdvance(HERBLORE_STAT, row.xp.toDouble() / 2.0)
            }
        }
        statAdvance(MAGIC_STAT, spell.castXp)
    }

    private fun ProtectedAccess.castDeathCharge(spell: MagicSpell) {
        val upgraded = player.vars["varbit.death_charge_scroll_used"] != 0
        play(8970, if (upgraded) 3288 else 1854, 5034)
        state.armDeathCharge(player)
        state.setCooldown(player, ArceuusCooldown.DeathCharge, DEATH_CHARGE_COOLDOWN)
        val foes = if (upgraded) "two foes" else "foe"
        mes("<col=a53fff>Upon the death of your next $foes, some of your special attack energy will be restored.</col>")
        finish(spell)
    }

    private fun ProtectedAccess.castWard(spell: MagicSpell) {
        play(8970, 1851, 5044)
        corruption.clear(player)
        ArceuusSpellState.ward(player, clock.cycle + player.statBase(MAGIC_STAT))
        state.setCooldown(player, ArceuusCooldown.Ward, WARD_COOLDOWN)
        mes("<col=a53fff>Your defence against Arceuus magic has been strengthened.</col>")
        finish(spell)
    }

    private fun ProtectedAccess.castOffering(spell: MagicSpell, ashes: Boolean) {
        play(8975, if (ashes) 1871 else 1872, if (ashes) 5051 else 5033)
        var restored = 0
        for ((slot, row) in eligibleOfferingSlots(ashes).take(MAX_OFFERINGS)) {
            if (inv[slot]?.id != row.item.id) continue
            if (invDel(inv, row.item.internalName, 1, slot = slot).failure) continue
            statAdvance(PRAYER_STAT, row.exp.toDouble() * OFFERING_XP_MULTIPLIER)
            restored += prayerRestore(row.item.internalName, ashes)
        }
        if (restored > 0) statHeal(PRAYER_STAT, constant = restored, percent = 0)
        state.setCooldown(player, ArceuusCooldown.Offering, OFFERING_COOLDOWN)
        finish(spell)
    }

    private suspend fun ProtectedAccess.castThrall(
        spell: MagicSpell,
        definition: ArceuusThrallDefinition,
    ) {
        if (actionDelay > mapClock) return
        if (state.onCooldown(player, ArceuusCooldown.Thrall)) {
            mes("You can only cast resurrection spells every 10 seconds.")
            return
        }
        val hasBook = inv.any { it?.id == BOOK_OF_THE_DEAD } || player.worn.any { it?.id == BOOK_OF_THE_DEAD }
        if (!hasBook) {
            mes("You must have a Book of the dead in your possession to use this spell.")
            return
        }
        if (player.stat(PRAYER_STAT) < definition.prayerDrain) {
            mes("You don't have enough Prayer points to cast that spell.")
            return
        }
        if (runes.attemptCast(player, spell).isFailure()) return

        player.statSub(PRAYER_STAT, definition.prayerDrain, percent = 0)
        play(8973, definition.castGraphic, definition.spawnSound)
        state.setCooldown(player, ArceuusCooldown.Thrall, THRALL_COOLDOWN)
        state.summon(player, definition, player.magicLvl)
        finish(spell)
    }

    private fun ProtectedAccess.eligibleHerbSlots(): List<Pair<Int, HerbloreCleaningRow>> =
        buildList {
            for (slot in inv.indices) {
                val obj = inv[slot] ?: continue
                val row = cleaningRows[obj.id] ?: continue
                if (row.statReq.all { player.stat(it.t0.internalName) >= it.t1 }) {
                    add(slot to row)
                }
            }
        }

    private fun ProtectedAccess.eligibleOfferingSlots(ashes: Boolean): List<Pair<Int, SkillPrayerRow>> =
        buildList {
            for (slot in inv.indices) {
                val obj = inv[slot] ?: continue
                val row = prayerRows[obj.id] ?: continue
                if (row.ashes != ashes) continue
                if (!ashes && row.item.internalName == SUPERIOR_DRAGON_BONES && player.statBase(PRAYER_STAT) < 70) {
                    continue
                }
                add(slot to row)
            }
        }

    private fun prayerRestore(item: String, ashes: Boolean): Int =
        if (ashes) {
            if (item == INFERNAL_ASHES) 2 else 1
        } else {
            if (item in DOUBLE_RESTORE_BONES) 2 else 1
        }

    private fun ProtectedAccess.play(animation: Int, graphic: Int, sound: Int) {
        player.anim(seq(animation), priority = 6)
        spotanim(spot(graphic))
        soundSynth(synth(sound))
    }

    private fun ProtectedAccess.finish(spell: MagicSpell) {
        actionDelay = mapClock + DEFAULT_ACTION_DELAY
        if (spell.castXp > 0.0) statAdvance(MAGIC_STAT, spell.castXp)
    }

    private companion object {
        const val MAGIC_STAT = "stat.magic"
        const val PRAYER_STAT = "stat.prayer"
        const val HERBLORE_STAT = "stat.herblore"
        const val BOOK_OF_THE_DEAD = 25818
        const val MAX_RUN_ENERGY = 1000
        const val RUN_ENERGY_PER_PRAYER = 10
        const val DEFAULT_ACTION_DELAY = 2
        const val DEGRIME_ACTION_DELAY = 8
        const val CORRUPTION_COOLDOWN = 50
        const val DEATH_CHARGE_COOLDOWN = 100
        const val OFFERING_COOLDOWN = 8
        const val SHADOW_VEIL_COOLDOWN = 50
        const val THRALL_COOLDOWN = 17
        const val VILE_VIGOUR_COOLDOWN = 17
        const val WARD_COOLDOWN = 50
        const val MAX_OFFERINGS = 3
        const val OFFERING_XP_MULTIPLIER = 3.0
        const val INFERNAL_ASHES = "obj.infernal_ashes"
        const val SUPERIOR_DRAGON_BONES = "obj.dragon_bones_superior"
        val DOUBLE_RESTORE_BONES =
            setOf(
                "obj.ourg_bones",
                "obj.dagannoth_bones",
                "obj.hydra_bones",
                SUPERIOR_DRAGON_BONES,
            )
    }
}


internal enum class ArceuusUtilitySpell(val spellObj: String) {
    MarkOfDarkness("obj.br_bass"),
    LesserCorruption("obj.zanarischoir_dummy"),
    GreaterCorruption("obj.skillcape_ardy_hood_firecape"),
    ShadowVeil("obj.placeholder_skillpethunter_grey"),
    VileVigour("obj.placeholder_snakepet_blue"),
    Degrime("obj.placeholder_skillpethunter_black"),
    DeathCharge("obj.placeholder_vetion_pet2"),
    WardOfArceuus("obj.placeholder_skillcape_ardy_hood_firecape"),
    DemonicOffering("obj.placeholder_skillpethunter_gold"),
    SinisterOffering("obj.poh_temp_limestonebrick"),
}

internal object ArceuusThralls {
    val definitions =
        listOf(
            thrall("obj.osb8_monkey_cow", "lesser ghostly thrall", 10878, 2, 1, 6, 1873, ThrallKind.Ghost),
            thrall("obj.osb8_monkey_bob", "lesser skeletal thrall", 10881, 2, 1, 6, 1874, ThrallKind.Skeleton),
            thrall("obj.osb8_monkey_donie", "lesser zombified thrall", 10884, 2, 1, 1, 1875, ThrallKind.Zombie),
            thrall("obj.osb8_bamboo", "superior ghostly thrall", 10879, 4, 2, 6, 1873, ThrallKind.Ghost),
            thrall("obj.osb8_monkey_unicorn", "superior skeletal thrall", 10882, 4, 2, 6, 1874, ThrallKind.Skeleton),
            thrall("obj.osb8_monkey_sheep", "superior zombified thrall", 10885, 4, 2, 1, 1875, ThrallKind.Zombie),
            thrall("obj.osb8_monkey_trap", "greater ghostly thrall", 10880, 6, 3, 6, 1873, ThrallKind.Ghost),
            thrall("obj.osb8_monkey_aereck", "greater skeletal thrall", 10883, 6, 3, 6, 1874, ThrallKind.Skeleton),
            thrall("obj.osb8_unicorn_horn", "greater zombified thrall", 10886, 6, 3, 1, 1875, ThrallKind.Zombie),
        )

    private fun thrall(
        spellObj: String,
        displayName: String,
        npcId: Int,
        prayerDrain: Int,
        maxHit: Int,
        range: Int,
        castGraphic: Int,
        kind: ThrallKind,
    ) =
        ArceuusThrallDefinition(
            spellObj = spellObj,
            displayName = displayName,
            npcId = npcId,
            prayerDrain = prayerDrain,
            maxHit = maxHit,
            attackRange = range,
            castGraphic = castGraphic,
            spawnAnimation = kind.spawnAnimation,
            spawnGraphic = kind.spawnGraphic,
            spawnSound = kind.spawnSound,
            attackAnimation = kind.attackAnimation,
            attackSound = kind.attackSound,
            hitSound = kind.hitSound,
            projectile = kind.projectile,
            hitType = kind.hitType,
        )

    private enum class ThrallKind(
        val spawnAnimation: Int,
        val spawnGraphic: Int,
        val spawnSound: Int,
        val attackAnimation: Int,
        val attackSound: Int,
        val hitSound: Int?,
        val projectile: Int?,
        val hitType: HitType,
    ) {
        Ghost(13683, 1903, 5061, 11101, 211, 212, 1907, HitType.Magic),
        Skeleton(13684, 1904, 5025, 13672, 2700, null, 1906, HitType.Ranged),
        Zombie(9046, 1905, 5040, 13681, 918, null, null, HitType.Melee),
    }
}

private fun seq(id: Int): String = RSCM.getReverseMapping(RSCMType.SEQ, id)
private fun spot(id: Int): String = RSCM.getReverseMapping(RSCMType.SPOTANIM, id)
private fun synth(id: Int): String = RSCM.getReverseMapping(RSCMType.SYNTH, id)
