package org.rsmod.content.other.special.attacks.melee

import dev.openrune.types.NpcServerType
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.CombatStance
import org.rsmod.api.combat.commons.styles.MeleeAttackStyle
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.specials.SpecialAttackManager
import org.rsmod.api.specials.SpecialAttackMap
import org.rsmod.api.specials.SpecialAttackRepository
import org.rsmod.api.specials.combat.MeleeSpecialAttack
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.HitImpactHandler
import org.rsmod.game.hit.HitType
import org.rsmod.game.hit.Hitmark

/**
 * S08 regression through real weapon registration, attack bodies and native Hit.dispatchImpact. The
 * NPC and its stat setters are real; rolls, queue scheduling and presentation are isolated. Final
 * damage is supplied at the native impact boundary, not inferred from the pre-rolled hit.
 *
 * The configured 250 example follows retained Nex W1 (revision 15325291: defence 260) and W2
 * (revision 15331654: at most 10 defence levels drained), indexed in NEX_OSRS_AUDIT.md S08. Weapon
 * timing, percentage arithmetic, Bone's base-level gate and BGS attempted-drain overflow
 * characterize the existing production handlers; these tests do not certify new OSRS mechanics.
 */
class NpcDefenceDrainSpecialAttackTest {
    @Test
    fun `warhammer and elder maul clamp at impact while preserving reductions above the floor`() {
        for (weapon in percentageWeapons) {
            val f = Fixture(weapon.map())
            val hit = f.launch(weapon.name)
            assertEquals(260, f.target.defenceLvl, weapon.name)

            hit.dispatchImpact(f.target, damage = 1)
            assertEquals(250, f.target.defenceLvl, weapon.name)
            f.launch(weapon.name).dispatchImpact(f.target, damage = 1)
            assertEquals(250, f.target.defenceLvl, weapon.name)

            f.target.defenceLvl = 400
            f.launch(weapon.name).dispatchImpact(f.target, damage = 1)
            assertEquals(weapon.boostedResult, f.target.defenceLvl, weapon.name)
            f.launch(weapon.name).dispatchImpact(f.target, damage = 1)
            assertEquals(250, f.target.defenceLvl, weapon.name)
        }
    }

    @Test
    fun `bandos uses final damage and clamps before the next stat read`() {
        val f = Fixture(ImpactMeleeSpecialAttacks())
        val small = f.launch(BANDOS, rolledDamage = 80)
        assertEquals(260, f.target.defenceLvl)
        assertEquals(80, small.damage)

        small.dispatchImpact(f.target, damage = 5)
        assertEquals(255, f.target.defenceLvl)
        assertEquals(200, f.target.strengthLvl)

        f.launch(BANDOS, rolledDamage = 80).dispatchImpact(f.target, damage = 30)
        assertEquals(250, f.target.defenceLvl)
        assertEquals(200, f.target.strengthLvl)
    }

    @Test
    fun `bandos preserves existing attempted-drain overflow when the floor rejects part of a drain`() {
        val f = Fixture(ImpactMeleeSpecialAttacks())

        f.launch(BANDOS, rolledDamage = 300).dispatchImpact(f.target, damage = 300)

        assertEquals(250, f.target.defenceLvl)
        assertEquals(
            160,
            f.target.strengthLvl,
            "Existing overflow consumes the attempted 260 first",
        )
        assertEquals(315, f.target.attackLvl)
        assertEquals(230, f.target.magicLvl)
        assertEquals(350, f.target.rangedLvl)
    }

    @Test
    fun `bone dagger applies the floor only through its existing base defence gate`() {
        val f = Fixture(BoneDaggerSpecialAttack())
        val hit = f.launch(BONE, rolledDamage = 30)
        assertEquals(260, f.target.defenceLvl)

        hit.dispatchImpact(f.target, damage = 30)
        assertEquals(250, f.target.defenceLvl)

        f.target.defenceLvl = 255
        f.launch(BONE, rolledDamage = 30).dispatchImpact(f.target, damage = 30)
        assertEquals(
            255,
            f.target.defenceLvl,
            "Below-base targets remain ineligible for Backstab drain",
        )

        f.target.defenceLvl = 275
        f.launch(BONE, rolledDamage = 30).dispatchImpact(f.target, damage = 30)
        assertEquals(250, f.target.defenceLvl)
    }

    @Test
    fun `zero final damage never invokes a stat reduction even after a positive roll`() {
        for (weapon in allWeapons) {
            val f = Fixture(weapon.map())
            val hit = f.launch(weapon.name, rolledDamage = 80)
            assertEquals(80, hit.damage, weapon.name)

            hit.dispatchImpact(f.target, damage = 0)

            assertEquals(260, f.target.defenceLvl, weapon.name)
        }
    }

    @Test
    fun `ordinary NPCs retain the production percentage and flat drains`() {
        for (weapon in allWeapons) {
            val f = Fixture(weapon.map(), minimum = null)

            f.launch(weapon.name, rolledDamage = 30).dispatchImpact(f.target, damage = 30)

            assertEquals(weapon.ordinaryResult, f.target.defenceLvl, weapon.name)
        }
    }

    @Test
    fun `stat immunity still rejects each weapon drain with a minimum configured`() {
        for (weapon in allWeapons) {
            val f = Fixture(weapon.map())
            f.target.combatImmunities.statDrain = true

            f.launch(weapon.name, rolledDamage = 30).dispatchImpact(f.target, damage = 30)

            assertEquals(260, f.target.defenceLvl, weapon.name)
        }
    }

    @Test
    fun `a minimum above current defence cannot heal through a weapon impact`() {
        for (weapon in allWeapons) {
            val f = Fixture(weapon.map(), minimum = null)
            f.target.defenceLvl = 200
            f.target.defenceDrainMinimum = 250

            f.launch(weapon.name, rolledDamage = 30).dispatchImpact(f.target, damage = 30)

            assertEquals(200, f.target.defenceLvl, weapon.name)
        }
    }

    private class Fixture(map: SpecialAttackMap, minimum: Int? = 250) {
        val target: Npc =
            spy(
                Npc(
                    NpcServerType(
                        id = 1,
                        name = "native special defence-floor target",
                        defence = 260,
                        strength = 200,
                        attack = 315,
                        magic = 230,
                        ranged = 350,
                        hitpoints = 3400,
                    )
                )
            )
        private val source = mock(Player::class.java)
        private val access =
            mock(ProtectedAccess::class.java) { invocation ->
                if (invocation.method.name == "getPlayer") source
                else RETURNS_DEFAULTS.answer(invocation)
            }
        private val registered = mutableMapOf<String, MeleeSpecialAttack>()
        private val queued = mutableListOf<Hit>()
        private var rolledDamage = 30
        private val manager =
            mock(SpecialAttackManager::class.java) { invocation ->
                when (invocation.method.name) {
                    "rollMeleeDamage",
                    "rollMeleeMaxHit" -> rolledDamage
                    "rollMeleeAccuracy" -> true
                    "queueMeleeHit" -> {
                        assertEquals(
                            1,
                            invocation.getArgument<Int>(3),
                            "Preserve native queue delay",
                        )
                        val handler = invocation.getArgument<HitImpactHandler?>(4)
                        assertNotNull(
                            handler,
                            "The registered weapon must supply its impact handler",
                        )
                        Hit(
                                type = HitType.Melee,
                                hitmark =
                                    Hitmark.fromNoSource(
                                        self = 0,
                                        source = 0,
                                        public = 0,
                                        damage = invocation.getArgument(2),
                                        delay = 0,
                                    ),
                                sourceUid = null,
                                righthandObj = null,
                                secondaryObj = null,
                                impactHandler = handler,
                            )
                            .also(queued::add)
                    }
                    else -> RETURNS_DEFAULTS.answer(invocation)
                }
            }

        init {
            target.defenceDrainMinimum = minimum
            // Elder Maul's impact visual is unrelated to the stat policy; all stat methods stay
            // real.
            doNothing().`when`(target).spotanim(anyString(), anyInt(), anyInt(), anyInt())
            val repository =
                mock(SpecialAttackRepository::class.java) { invocation ->
                    if (invocation.method.name == "registerMelee") {
                        registered[invocation.getArgument(0)] = invocation.getArgument(1)
                        null
                    } else {
                        RETURNS_DEFAULTS.answer(invocation)
                    }
                }
            with(map) { repository.register(manager) }
        }

        fun launch(weapon: String, rolledDamage: Int = 30): Hit {
            this.rolledDamage = rolledDamage
            val attack =
                CombatAttack.Melee(
                    weapon = null,
                    type = MeleeAttackType.Crush,
                    style = MeleeAttackStyle.Accurate,
                    stance = CombatStance.Stance1,
                )
            val before = queued.size
            val launched = synchronous {
                with(registered.getValue(weapon)) { access.attack(target, attack) }
            }
            assertTrue(launched, weapon)
            assertEquals(before + 1, queued.size, weapon)
            return queued.last()
        }
    }

    private data class Weapon(
        val name: String,
        val map: () -> SpecialAttackMap,
        val ordinaryResult: Int,
        val boostedResult: Int = 0,
    )

    private companion object {
        const val BANDOS = "obj.bgs"
        const val BONE = "obj.dttd_bone_dagger"
        val percentageWeapons =
            listOf(
                Weapon("obj.dragon_warhammer", ::ImpactMeleeSpecialAttacks, 182, 280),
                Weapon("obj.elder_maul", ::ElderMaulSpecialAttack, 169, 260),
            )
        val allWeapons =
            percentageWeapons +
                listOf(
                    Weapon(BANDOS, ::ImpactMeleeSpecialAttacks, 230),
                    Weapon(BONE, ::BoneDaggerSpecialAttack, 230),
                )

        fun <T> synchronous(block: suspend () -> T): T {
            var completed: Result<T>? = null
            block.startCoroutine(
                object : Continuation<T> {
                    override val context = EmptyCoroutineContext

                    override fun resumeWith(result: Result<T>) {
                        completed = result
                    }
                }
            )
            return checkNotNull(completed) { "This registered special unexpectedly suspended" }
                .getOrThrow()
        }
    }
}
