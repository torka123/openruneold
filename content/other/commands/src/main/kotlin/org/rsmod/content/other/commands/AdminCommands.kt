package org.rsmod.content.other.commands

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import kotlin.math.max
import kotlin.math.min
import org.rsmod.annotations.InternalApi
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.CombatStance
import org.rsmod.api.combat.commons.styles.MagicAttackStyle
import org.rsmod.api.combat.commons.styles.MeleeAttackStyle
import org.rsmod.api.combat.commons.styles.RangedAttackStyle
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.commons.types.RangedAttackType
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.combat.weapon.types.AttackTypes
import org.rsmod.api.death.prepareAdminDieTest
import org.rsmod.api.death.preparePvpDeath
import org.rsmod.api.instances.BossInstanceRegistry
import org.rsmod.api.instances.InstanceArea
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.invtx.invClear
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.mechanics.toxins.impl.PlayerDisease
import org.rsmod.api.mechanics.toxins.impl.PlayerPoison
import org.rsmod.api.mechanics.toxins.impl.PlayerVenom
import org.rsmod.api.player.cheat.ADMIN_GOD_HITPOINTS_MAX
import org.rsmod.api.player.cheat.AdminItemSearchState
import org.rsmod.api.player.cheat.adminGodHitpoints
import org.rsmod.api.player.cheat.adminGodMode
import org.rsmod.api.player.cheat.adminItemSearch
import org.rsmod.api.player.cheat.adminMaxHit
import org.rsmod.api.player.cheat.adminOneHit
import org.rsmod.api.player.clearInteractionRoute
import org.rsmod.api.player.combatClearQueue
import org.rsmod.api.player.hat
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.ironman.PlayerGamemode
import org.rsmod.api.player.ironman.setGamemode
import org.rsmod.api.player.legs
import org.rsmod.api.player.output.MiscOutput
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.queueDeath
import org.rsmod.api.player.quiver
import org.rsmod.api.player.ranged.BlowpipeAmmo
import org.rsmod.api.player.righthand
import org.rsmod.api.player.stat.PlayerSkillXP
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.player.stat.statHeal
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.player.torso
import org.rsmod.api.player.ui.PlayerInterfaceUpdates
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.resyncVar
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.specials.SpecialAttack
import org.rsmod.api.specials.SpecialAttackRegistry
import org.rsmod.api.specials.energy.SpecialAttackEnergy
import org.rsmod.api.utils.format.formatAmount
import org.rsmod.api.utils.system.SafeServiceExit
import org.rsmod.content.interfaces.bank.BankTab
import org.rsmod.game.GameUpdate
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.Cheat
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.interact.InteractionNpcOp
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.stat.PlayerSkillXPTable
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.map.zone.ZoneGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.objtx.TransactionResult
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.loc.LocLayerConstants
import org.rsmod.utils.bits.withBits
import org.simmetrics.metrics.StringMetrics

class AdminCommands
@Inject
constructor(
    private val protectedAccess: ProtectedAccessLauncher,
    private val playerList: PlayerList,
    private val locRepo: LocRepository,
    private val npcRepo: NpcRepository,
    private val update: GameUpdate,
    private val areaChecker: AreaChecker,
    private val regions: RegionRegistry,
    private val instanceRegistry: BossInstanceRegistry,
    private val instances: InstanceManager,
    private val clock: MapClock,
    private val specialAttacks: SpecialAttackRegistry,
    private val specialEnergy: SpecialAttackEnergy,
    private val attackStyles: AttackStyles,
    private val attackTypes: AttackTypes,
    private val collision: CollisionFlagMap,
) : PluginScript() {
    private val logger = InlineLogger()

    private val levenshteinMetric = StringMetrics.levenshtein()

    private companion object {
        private const val SEARCH_PAGE_SIZE = 8
        private const val MAX_SEARCH_RESULTS = 100
        private const val SPECIAL_TARGET_HITPOINTS = 100_000
        private const val SPECIAL_TARGET_DURATION_CYCLES = 100_000
    }

    private var Player.insideWilderness by boolVarBit("varbit.inside_wilderness")

    override fun ScriptContext.startup() {
        onCommand("master", "Max out all stats", ::master)
        onCommand("reset", "Reset all stats", ::reset)
        onCommand("mypos", "Get current coordinates", ::mypos)
        onCommand("tele", "Teleport to coordgrid", ::tele) {
            invalidArgs = "Usage: ::tele mx mz [level](e.g. ::tele 3200 3200 0)"
        }
        onCommand("barrows", "Teleport to the Barrows entrance", ::barrows)
        onCommand("prif", "Teleport to the Prifddinas Agility Course", ::prifAgility)
        onCommand("prifagility", "Teleport to the Prifddinas Agility Course", ::prifAgility)
        onCommand("telezone", "Teleport to zone key", ::teleZone) {
            invalidArgs = "Use as ::telezone zoneX zoneY level (ex: 400 400 0)"
        }
        onCommand("up", "Teleport up levels", ::up) {
            invalidArgs = "Use as ::up [amount] (ex: ::up 2)"
        }
        onCommand("down", "Teleport down levels", ::down) {
            invalidArgs = "Use as ::down [amount] (ex: ::down 2)"
        }
        onCommand("anim", "Play animation", ::anim)
        onCommand("spot", "Play spotanim", ::spotanim) {
            invalidArgs = "Use as ::spot spotanimDebugNameOrId (ex: fx_emote_party01_active)"
        }
        onCommand("synth", "Play synth sound", ::synth) {
            invalidArgs = "Use as ::synth idOrName (ex: ::synth 3600 or ::synth pillory_wrong)"
        }
        onCommand("object", "Spawn loc", ::locAdd) {
            invalidArgs = "Use as ::object duration locDebugNameOrId (ex: 100 bookcase)"
        }

        onCommand("locadd", "Spawn loc", ::locAdd) {
            invalidArgs = "Use as ::locadd duration locDebugNameOrId (ex: 100 bookcase)"
        }
        onCommand("locdel", "Remove loc", ::locDel) { invalidArgs = "Use as ::locdel duration" }
        onCommand("objectdel", "Remove loc", ::locDel) {
            invalidArgs = "Use as ::objectdel duration"
        }

        onCommand("npc", "Spawn npc", ::npcAdd) {
            invalidArgs = "Use as ::npc duration npcDebugNameOrId (ex: 100 prison_pete)"
        }

        onCommand("npcadd", "Spawn npc", ::npcAdd) {
            invalidArgs = "Use as ::npcadd duration npcDebugNameOrId (ex: 100 prison_pete)"
        }

        onCommand("invadd", "Spawn obj into inv", ::invAdd)
        onCommand("item", "Spawn an item into your inventory", ::invAdd) {
            invalidArgs = "Usage: ::item <object-id> [quantity] (e.g. ::item 4151 1)"
        }
        onCommand("find", "Find item IDs", ::find) {
            invalidArgs = "Usage: ::find <item name or item id>"
        }
        onCommand("findnext", "Show the next item search results page", ::findNext) {
            invalidArgs = "Usage: ::findnext"
        }
        onCommand("findprevious", "Show the previous item search results page", ::findPrevious) {
            invalidArgs = "Usage: ::findprevious"
        }

        onCommand("invclear", "Remove all objs from inv", ::invClear)
        onCommand(
            "specialbank",
            "Add every special weapon and its complete test kit to your bank",
            ::specialBank,
        )
        onCommand(
            "specialtarget",
            "Spawn a stationary 100,000-HP special-attack test target",
            ::specialTarget,
        ) {
            invalidArgs = "Usage: ::specialtarget [vampire]"
        }
        onCommand(
            "specialaudit",
            "Run every registered special through the real PvN interaction pipeline",
            ::specialAudit,
        ) {
            invalidArgs =
                "Usage: ::specialaudit [cycles-per-weapon, 6-12] [start-index] [end-index]"
        }
        onCommand("varp", "Set varp value", ::setVarp) {
            invalidArgs = "Use as ::varp debugNameOrId value (ex: option_run 1)"
        }
        onCommand("varbit", "Set varbit value", ::setVarBit) {
            invalidArgs = "Use as ::varbit debugNameOrId value (ex: emote_hotline_bling 1)"
        }
        onCommand("getvarp", "Get varp value", ::getVarp) {
            invalidArgs = "Use as ::getvarp debugNameOrId (ex: option_run)"
        }
        onCommand("getvarbit", "Get varbit value", ::getVarBit) {
            invalidArgs = "Use as ::getvarbit debugNameOrId (ex: emote_hotline_bling)"
        }
        onCommand("reboot", "Reboots the game world, applying packed changes", ::reboot)
        onCommand("slowreboot", "Reboots the game world, with a timer", ::slowReboot)
        onCommand(
            "poison",
            "Test player poison (wiki initial damage, optional raw severity)",
            ::poisonTest,
        ) {
            invalidArgs =
                "Use as ::poison initialDamage [severity] (e.g. ::poison 8 or ::poison 0 36)"
        }
        onCommand("venom", "Test player venom (escalating damage timer)", ::venomTest)
        onCommand("venomclear", "Clears Venom", ::venomClear)
        onCommand("disease", "Test disease (drain per tick, default 3)", ::diseaseTest) {
            invalidArgs = "Use as ::disease [drainPerTick] (e.g. ::disease 5)"
        }
        onCommand(
            "diseaseclear",
            "Clears disease timer (stats recover via normal regen)",
            ::diseaseClear,
        )
        onCommand("die", "Simulate death: ::die pvm|pvp [true=in wildy]", ::dieTest) {
            invalidArgs =
                "Usage: ::die pvm|pvp [true|false]  (second arg = in Wilderness, default false)"
        }
        onCommand("god", "Toggle 10,000,000 damageable hitpoints", ::god)
        onCommand("god2", "Toggle invulnerability and one-hit attacks", ::god2)
        onCommand("godmode", "Toggle administrator god mode", ::godmode) {
            invalidArgs = "Usage: ::godmode [on/off]"
        }
        onCommand("maxhit", "Toggle always max hit", ::maxhit)
        onCommand("spec", "Restore special-attack energy", ::spec)
        onCommand("openbank", "Open the bank", ::bank)
        onCommand("transmog", "Transmog player to NPC appearance (no args to reset)", ::transmog) {
            invalidArgs = "Use as ::transmog npcNameOrId (ex: goblin or 126) or ::transmog to reset"
        }
        onCommand("gamemode", "Set account gamemode (normal|ironman|uim|hcim)", ::gamemode) {
            invalidArgs = "Use as ::gamemode normal|ironman|uim|hcim"
        }
        onCommand("instanceexit", "Teleport to an instance's exit coord", ::instanceExit) {
            invalidArgs = "Use as ::instanceexit instanceKey (ex: ::instanceexit graardor)"
        }
    }

    private fun gamemode(cheat: Cheat) =
        with(cheat) {
            val mode =
                when (args.getOrNull(0)?.lowercase()) {
                    "normal",
                    "main",
                    "0" -> PlayerGamemode.NORMAL
                    "ironman",
                    "iron",
                    "1" -> PlayerGamemode.IRONMAN
                    "uim",
                    "ultimate",
                    "2" -> PlayerGamemode.ULTIMATE_IRONMAN
                    "hcim",
                    "hardcore",
                    "3" -> PlayerGamemode.HARDCORE_IRONMAN
                    else -> {
                        player.mes("Use as ::gamemode normal|ironman|uim|hcim")
                        return
                    }
                }
            player.setGamemode(mode)
            player.mes("Gamemode set to $mode (varbit.ironman synced; persists on logout).")
        }

    private fun god(cheat: Cheat) =
        with(cheat) {
            val enabled = player.adminGodHitpoints == null
            player.adminGodMode = false
            player.adminOneHit = false
            player.adminGodHitpoints = if (enabled) ADMIN_GOD_HITPOINTS_MAX else null
            player.statHeal("stat.hitpoints", constant = 0, percent = 100)
            if (enabled) {
                PlayerPoison.clear(player)
                PlayerVenom.clear(player)
                PlayerDisease.clear(player)
                player.mes("God enabled: 10,000,000 HP. NPC hits still deal damage.")
            } else {
                player.mes("God disabled: normal hitpoints restored.")
            }
        }

    private fun god2(cheat: Cheat) =
        with(cheat) {
            val enabled = !(player.adminGodMode && player.adminOneHit)
            player.adminGodHitpoints = null
            player.adminGodMode = enabled
            player.adminOneHit = enabled
            player.statHeal("stat.hitpoints", constant = 0, percent = 100)
            if (enabled) {
                PlayerPoison.clear(player)
                PlayerVenom.clear(player)
                PlayerDisease.clear(player)
                player.mes(
                    "God2 enabled: incoming damage blocked and attacks against NPCs are one-hit."
                )
            } else {
                player.mes("God2 disabled.")
            }
        }

    private fun godmode(cheat: Cheat) =
        with(cheat) {
            val enabled =
                when (args.singleOrNull()?.lowercase()) {
                    null -> !player.adminGodMode
                    "on" -> true
                    "off" -> false
                    else -> {
                        player.mes("Usage: ::godmode [on/off]")
                        return@with
                    }
                }
            player.adminGodHitpoints = null
            player.adminGodMode = enabled
            player.adminOneHit = false
            if (enabled) {
                PlayerPoison.clear(player)
                PlayerVenom.clear(player)
                PlayerDisease.clear(player)
                player.statHeal("stat.hitpoints", constant = 0, percent = 100)
                player.statHeal("stat.prayer", constant = 0, percent = 100)
                player.mes("God mode enabled: incoming damage is blocked.")
            } else {
                player.mes("God mode disabled.")
            }
            logger.info {
                "Administrator '${player.displayName}' ${if (enabled) "enabled" else "disabled"} god mode."
            }
        }

    private fun maxhit(cheat: Cheat) =
        with(cheat) {
            player.adminMaxHit = !player.adminMaxHit
            player.mes("Max hit ${if (player.adminMaxHit) "enabled" else "disabled"}.")
        }

    private fun spec(cheat: Cheat) =
        with(cheat) {
            if (args.isNotEmpty()) {
                player.mes("Usage: ::spec")
                return@with
            }
            val restored = specialEnergy.restoreFullSpecialEnergy(player)
            player.mes(
                if (restored == 0) {
                    "Your special attack energy is already full."
                } else {
                    "Your special attack energy has been fully restored."
                }
            )
        }

    private fun poisonTest(cheat: Cheat) =
        with(cheat) {
            val initialDamage = args.getOrNull(0)?.toIntOrNull() ?: 0
            val severity = args.getOrNull(1)?.toIntOrNull() ?: 0
            val ok =
                PlayerPoison.tryPoison(player, initialDamage = initialDamage, severity = severity)
            player.mes(
                if (ok) {
                    "Poison applied (initialDamage=$initialDamage severityParam=$severity)."
                } else {
                    "Poison not applied (weaker/equal than current, or both inputs zero)."
                }
            )
        }

    private fun venomTest(cheat: Cheat) = with(cheat) { PlayerVenom.tryVenom(player) }

    private fun venomClear(cheat: Cheat) = with(cheat) { PlayerVenom.clear(player) }

    private fun diseaseTest(cheat: Cheat) =
        with(cheat) {
            val drain = args.getOrNull(0)?.toIntOrNull() ?: 3
            val ok = PlayerDisease.tryDisease(player, drain)
            player.mes(
                if (ok) {
                    "Disease applied (drain per tick=$drain)."
                } else {
                    "Disease not applied (no eligible skill)."
                }
            )
        }

    private fun diseaseClear(cheat: Cheat) =
        with(cheat) {
            PlayerDisease.clear(player)
            player.mes("Disease cleared.")
        }

    private fun master(cheat: Cheat) = with(cheat) { player.setStatLevels(level = 99) }

    private fun reset(cheat: Cheat) = with(cheat) { player.setStatLevels(level = 1) }

    private fun mypos(cheat: Cheat) =
        with(cheat) {
            player.mes("${player.coords}:")
            player.mes("  ${ZoneKey.from(player.coords)} - ${ZoneGrid.from(player.coords)}")
            player.mes(
                "  ${MapSquareKey.from(player.coords)} - ${MapSquareGrid.from(player.coords)}"
            )
            player.mes("  BuildArea(${player.buildArea})")
        }

    private fun tele(cheat: Cheat) =
        with(cheat) {
            val args = if (args.size == 1) args[0].split(",") else args
            val x = args[0].toInt()
            val y = args[1].toInt()
            val level = args.getOrNull(2)?.toInt() ?: 0
            val coords = CoordGrid(x, y, level)
            protectedAccess.launch(player) {
                player.mes("Teleported to $coords.")
                instances.leaveForExternalTeleport(player, coords, clock.cycle)
                telejump(coords, TeleportType.Exempt)
            }
        }

    private fun barrows(cheat: Cheat) =
        with(cheat) {
            val destination = CoordGrid(3564, 3288, 0)
            protectedAccess.launch(player) {
                player.mes("Teleported to Barrows. Talk to the Strange Old Man to begin.")
                instances.leaveForExternalTeleport(player, destination, clock.cycle)
                telejump(destination, TeleportType.Exempt)
            }
        }

    private fun prifAgility(cheat: Cheat) =
        with(cheat) {
            val destination = CoordGrid(3253, 6109, 0)
            protectedAccess.launch(player) {
                player.mes("Teleported to the Prifddinas Agility Course starting ladder.")
                instances.leaveForExternalTeleport(player, destination, clock.cycle)
                telejump(destination, TeleportType.Exempt)
            }
        }

    private fun teleZone(cheat: Cheat) =
        with(cheat) {
            val args = if (args.size == 1) args[0].split(",") else args
            val zoneX = args[0].toInt()
            val zoneZ = args[1].toInt()
            val level = args[2].toInt()
            val coords = ZoneKey(zoneX, zoneZ, level).toCoords()
            protectedAccess.launch(player) {
                player.mes("Teleported to $coords.")
                instances.leaveForExternalTeleport(player, coords, clock.cycle)
                telejump(coords, TeleportType.Exempt)
            }
        }

    private fun up(cheat: Cheat) {
        teleLevel(cheat, levelDelta = 1)
    }

    private fun down(cheat: Cheat) {
        teleLevel(cheat, levelDelta = -1)
    }

    private fun teleLevel(cheat: Cheat, levelDelta: Int) {
        with(cheat) {
            val amount =
                when {
                    args.isEmpty() -> 1
                    else -> {
                        val parsed = args[0].toIntOrNull()
                        if (parsed == null) {
                            player.mes("Invalid amount: '${args[0]}'")
                            return@with
                        }
                        parsed
                    }
                }
            if (amount <= 0) {
                player.mes("Amount must be positive.")
                return@with
            }
            val current = player.coords
            val destLevel =
                (current.level + levelDelta * amount).coerceIn(0, CoordGrid.LEVEL_BIT_MASK)
            if (destLevel == current.level) {
                val direction = if (levelDelta > 0) "up" else "down"
                player.mes("Cannot go $direction from level ${current.level}.")
                return@with
            }
            val dest = current.copy(level = destLevel)
            protectedAccess.launch(player) {
                player.mes("Teleported to $dest.")
                instances.leaveForExternalTeleport(player, dest, clock.cycle)
                telejump(dest, TeleportType.Exempt)
            }
        }
    }

    private fun anim(cheat: Cheat) =
        with(cheat) {
            val typeId = RSCM.getRSCM("seq.${args.asTypeName()}")
            if (typeId == -1) {
                player.mes("There is no seq mapped to: '${args.asTypeName()}'")
                return
            }
            val type = ServerCacheManager.getAnim(typeId)
            if (type == null) {
                player.mes("That seq does not exist: $typeId")
                return
            }
            player.anim("seq.${args.asTypeName()}")
            player.mes("Anim: '${args.asTypeName()}' (priority=${type.priority})")
            logger.debug { "Anim: $type" }
        }

    private fun spotanim(cheat: Cheat) =
        with(cheat) {
            val (typeName, heightArg) = args.asTypeNameAndNumber(defaultNumber = 0)
            val typeId = "spotanim.${typeName}".asRSCM()
            if (typeId == -1) {
                player.mes("There is no spotanim mapped to: '${typeName}'")
                return
            }

            val height = min(heightArg.toInt(), Short.MAX_VALUE.toInt())
            player.spotanim("spotanim.${typeName}", delay = 0, height = height, slot = 0)
            player.mes("Spotanim: '${typeName}' (height=$height)")
            logger.debug { "Spotanim: $typeName" }
        }

    private fun synth(cheat: Cheat) =
        with(cheat) {
            val arg = args.getOrNull(0)
            if (arg == null) {
                player.mes("Use as ::synth idOrName (ex: ::synth 3600 or ::synth pillory_wrong)")
                return
            }
            val id = arg.toIntOrNull()
            if (id != null) {
                player.soundSynth(id)
                player.mes("Synth: $id")
                return
            }
            val typeName = arg.removePrefix("synth.")
            val typeId = "synth.$typeName".asRSCM()
            if (typeId == -1) {
                player.mes("There is no synth mapped to: '$typeName'")
                return
            }
            player.soundSynth("synth.$typeName")
            player.mes("Synth: '$typeName' ($typeId)")
        }

    private fun locAdd(cheat: Cheat) =
        with(cheat) {
            val typeId = "loc.${args[1]}".asRSCM()

            val type = ServerCacheManager.getObject(typeId)!!
            if (type == null) {
                player.mes("That loc does not exist: $typeId")
                return
            }
            val duration = args[0].toInt()
            val angle = args.getOrNull(2)?.toInt() ?: LocAngle.West.id
            val shape = args.getOrNull(3)?.toInt() ?: LocShape.CentrepieceStraight.id
            val layer = LocLayerConstants.of(shape)
            val loc = LocInfo(layer, player.coords, LocEntity(type.id, shape, angle))
            locRepo.add(loc, duration)
            player.mes("Spawned loc '${type.internalName}' (duration: $duration cycles)")
            logger.debug { "Spawned loc: loc=$loc, type=$type" }
        }

    private fun locDel(cheat: Cheat) =
        with(cheat) {
            val zone = ZoneKey.from(player.coords)
            val locs = locRepo.findAll(zone).filter { it.coords == player.coords }.toList()
            if (locs.isEmpty()) {
                player.mes("No loc found on ${player.coords}")
                return
            }
            val duration = args[0].toInt()
            val shape = args.getOrNull(1)?.toIntOrNull() ?: LocShape.CentrepieceStraight.id
            val loc = locs.firstOrNull { it.shapeId == shape }
            if (loc == null) {
                player.mes("No loc with shape `${LocShape[shape]}` found on ${player.coords}")
                return
            }
            val type = ServerCacheManager.getObject(loc.id)!!
            locRepo.del(loc, duration)
            player.mes("Deleted loc `${type.internalName}` (duration: $duration cycles)")
            logger.debug { "Deleted loc: loc=$loc, type=$type" }
        }

    private fun npcAdd(cheat: Cheat) =
        with(cheat) {
            val typeId = "npc.${args[1]}".asRSCM()

            val type = ServerCacheManager.getNpc(typeId)
            if (type == null) {
                player.mes("That npc does not exist: $typeId")
                return
            }
            val duration = args[0].toInt()
            val npc = Npc(type, player.coords)
            npc.mode = NpcMode.None
            npcRepo.add(npc, duration)
            player.mes("Spawned npc `${args[1]}` (duration: $duration cycles)")
        }

    private fun find(cheat: Cheat) =
        with(cheat) {
            val query = args.joinToString(" ").trim()
            if (query.isEmpty()) {
                player.mes("Usage: ::find <item name or item id>")
                return@with
            }
            val matches = findItems(query)
            if (matches.isEmpty()) {
                player.adminItemSearch = null
                player.mes("No items found for '$query'.")
                return@with
            }
            val state =
                AdminItemSearchState(
                    query = query,
                    resultIds = matches.take(MAX_SEARCH_RESULTS).map(ItemServerType::id),
                    totalMatches = matches.size,
                )
            player.adminItemSearch = state

            if (matches.size > state.resultIds.size) {
                player.mes(
                    "Showing the top ${state.resultIds.size} of ${matches.size} matching items."
                )
            }
            displaySearchResults(player, state)
        }

    private fun findNext(cheat: Cheat) =
        with(cheat) {
            if (args.isNotEmpty()) {
                player.mes("Usage: ::findnext")
                return@with
            }
            val state =
                player.adminItemSearch
                    ?: run {
                        player.mes("Use ::find <item name or item id> first.")
                        return@with
                    }
            val lastPage = (state.resultIds.size - 1) / SEARCH_PAGE_SIZE
            if (state.page >= lastPage) {
                player.mes("This is already the last search results page.")
                return@with
            }
            val next = state.copy(page = state.page + 1)
            player.adminItemSearch = next
            displaySearchResults(player, next)
        }

    private fun findPrevious(cheat: Cheat) =
        with(cheat) {
            if (args.isNotEmpty()) {
                player.mes("Usage: ::findprevious")
                return@with
            }
            val state =
                player.adminItemSearch
                    ?: run {
                        player.mes("Use ::find <item name or item id> first.")
                        return@with
                    }
            if (state.page == 0) {
                player.mes("This is already the first search results page.")
                return@with
            }
            val previous = state.copy(page = state.page - 1)
            player.adminItemSearch = previous
            displaySearchResults(player, previous)
        }

    private fun findItems(query: String): List<ItemServerType> {
        val numericId = query.toIntOrNull()
        if (numericId != null) {
            return listOfNotNull(ServerCacheManager.getItem(numericId)).filter { item ->
                item.isSearchableDefinition()
            }
        }
        val normalized = query.lowercase()
        val words = normalized.split(Regex("\\s+")).filter(String::isNotBlank)
        return ServerCacheManager.getItemTypes()
            .asSequence()
            .filter { item -> item.isSearchableDefinition() }
            .filter { item ->
                val name = item.name.lowercase()
                name.contains(normalized) || words.all(name::contains) || words.any(name::contains)
            }
            .sortedWith(
                compareBy<ItemServerType>(
                    { item -> item.searchRank(normalized, words) },
                    { item -> item.name.lowercase() },
                    ItemServerType::id,
                )
            )
            .toList()
    }

    private fun ItemServerType.searchRank(query: String, words: List<String>): Int =
        when {
            name.equals(query, ignoreCase = true) -> 0
            name.startsWith(query, ignoreCase = true) -> 1
            words.all { word -> name.contains(word, ignoreCase = true) } -> 2
            else -> 3
        }

    private fun ItemServerType.isSearchableDefinition(): Boolean {
        val trimmedName = name.trim()
        return trimmedName.isNotEmpty() && !trimmedName.equals("null", ignoreCase = true)
    }

    private fun ItemServerType.searchLabel(): String {
        val trimmedName = name.trim()
        return if (isCert && !trimmedName.endsWith("(noted)", ignoreCase = true)) {
            "$trimmedName (noted)"
        } else {
            trimmedName
        }
    }

    private fun displaySearchResults(player: Player, state: AdminItemSearchState) {
        val lastPage = (state.resultIds.size - 1) / SEARCH_PAGE_SIZE
        val page = state.page.coerceIn(0, lastPage)
        if (page != state.page) {
            player.adminItemSearch = state.copy(page = page)
        }
        val start = page * SEARCH_PAGE_SIZE
        val end = min(start + SEARCH_PAGE_SIZE, state.resultIds.size)
        player.mes(
            "Item results for '${state.query}' (${state.totalMatches} matches, page ${page + 1}/${lastPage + 1}):"
        )
        for (index in start until end) {
            val item = ServerCacheManager.getItem(state.resultIds[index]) ?: continue
            player.mes("${index + 1}. ${item.searchLabel()} - ID ${item.id}")
        }
        player.mes("Use ::item <item ID> [amount] to spawn an item.")
        if (page < lastPage) {
            player.mes("Use ::findnext for more results.")
        }
        if (page > 0) {
            player.mes("Use ::findprevious for earlier results.")
        }
    }

    private fun invAdd(cheat: Cheat) =
        with(cheat) {
            val (typeName, countArg) = args.asTypeNameAndNumber(defaultNumber = 1)
            val normalizedName = "obj.$typeName"
            val itemId =
                typeName.toIntOrNull()
                    ?: try {
                        normalizedName.asRSCM(RSCMType.OBJ)
                    } catch (_: IllegalStateException) {
                        player.mes("Item `$typeName` does not exist.")
                        return@with
                    }
            val type =
                ServerCacheManager.getItem(itemId)
                    ?: run {
                        player.mes("Item `$typeName` does not exist.")
                        return@with
                    }

            val count = countArg.toLong().coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val objName = type.name.ifEmpty { normalizedName }

            val spawned = player.invAdd(player.inv, itemId, count, strict = false)
            if (spawned.err is TransactionResult.RestrictedDummyitem) {
                player.mes("You can't spawn this item!")
                return
            }
            player.mes("Spawned inv obj `$objName` x ${spawned.completed().formatAmount}")
        }

    private fun invClear(cheat: Cheat) = with(cheat) { player.invClear(player.inv) }

    private fun specialBank(cheat: Cheat) =
        with(cheat) {
            protectedAccess.launch(player) {
                val specialIds = specialAttacks.registeredItemIds()
                val supportItems =
                    mapOf(
                        "obj.dragon_arrow".asRSCM(RSCMType.OBJ) to 10_000,
                        "obj.rune_arrow".asRSCM(RSCMType.OBJ) to 10_000,
                        "obj.bronze_arrow".asRSCM(RSCMType.OBJ) to 10_000,
                        "obj.dragon_javelin".asRSCM(RSCMType.OBJ) to 10_000,
                        "obj.dttd_bone_crossbow_bolt".asRSCM(RSCMType.OBJ) to 10_000,
                        "obj.dragon_bolts".asRSCM(RSCMType.OBJ) to 10_000,
                        "obj.dragon_bolts_enchanted_ruby".asRSCM(RSCMType.OBJ) to 10_000,
                        "obj.dragon_bolts_enchanted_diamond".asRSCM(RSCMType.OBJ) to 10_000,
                        "obj.atlatl_dart".asRSCM(RSCMType.OBJ) to 10_000,
                        "obj.dragon_dart".asRSCM(RSCMType.OBJ) to 10_000,
                        "obj.snakeboss_scale".asRSCM(RSCMType.OBJ) to 100_000,
                        21_820 to 100_000, // Revenant ether; no stable revision-240 RSCM alias.
                        "obj.frost_moon_helm".asRSCM(RSCMType.OBJ) to 1,
                        "obj.frost_moon_chestplate".asRSCM(RSCMType.OBJ) to 1,
                        "obj.frost_moon_tassets".asRSCM(RSCMType.OBJ) to 1,
                        "obj.blood_moon_helm".asRSCM(RSCMType.OBJ) to 1,
                        "obj.blood_moon_chestplate".asRSCM(RSCMType.OBJ) to 1,
                        "obj.blood_moon_tassets".asRSCM(RSCMType.OBJ) to 1,
                        "obj.eclipse_moon_helm".asRSCM(RSCMType.OBJ) to 1,
                        "obj.eclipse_moon_chestplate".asRSCM(RSCMType.OBJ) to 1,
                        "obj.eclipse_moon_tassets".asRSCM(RSCMType.OBJ) to 1,
                    )
                val existingIds = bank.objs.asSequence().filterNotNull().map { it.id }.toHashSet()
                val missing = specialIds.filterNot(existingIds::contains).sorted()
                val missingSupport = supportItems.filterKeys { it !in existingIds }
                if (missing.isEmpty() && missingSupport.isEmpty()) {
                    mes(
                        "Your bank already contains all ${specialIds.size} special weapons and the complete test kit."
                    )
                    return@launch
                }

                val result =
                    player.invTransaction(bank) {
                        val target = select(bank)
                        for (id in missing) {
                            add(target, obj = id, count = 1, strict = true)
                        }
                        for ((id, count) in missingSupport) {
                            add(target, obj = id, count = count, strict = true)
                        }
                    }
                if (!result.success) {
                    mes(
                        "No items were added. You need enough free bank slots for ${missing.size + missingSupport.size} test-kit items."
                    )
                    return@launch
                }
                BankTab.repairLayout(player, bank)
                mes(
                    "Added ${missing.size} special weapons and ${missingSupport.size} support items (${specialIds.size} registered weapons total)."
                )
            }
        }

    private fun specialTarget(cheat: Cheat) =
        with(cheat) {
            val vampire = args.firstOrNull()?.equals("vampire", ignoreCase = true) == true
            val typeId =
                if (vampire) {
                    "npc.burgh_vampire_juvenile_1".asRSCM(RSCMType.NPC)
                } else {
                    2215 // Revision-240 General Graardor; no stable RSCM NPC symbol exists.
                }
            val type = requireNotNull(ServerCacheManager.getNpc(typeId))
            val npc = Npc(type, player.coords.translateX(if (vampire) 2 else 4))
            npc.mode = NpcMode.None
            npc.baseHitpointsLvl = SPECIAL_TARGET_HITPOINTS
            npc.hitpoints = if (vampire) SPECIAL_TARGET_HITPOINTS / 2 else SPECIAL_TARGET_HITPOINTS
            npcRepo.add(npc, SPECIAL_TARGET_DURATION_CYCLES)
            player.mes(
                "Spawned stationary ${type.name} special target with ${npc.hitpoints.formatAmount}/${SPECIAL_TARGET_HITPOINTS.formatAmount} HP."
            )
        }

    private fun specialAudit(cheat: Cheat) =
        with(cheat) {
            val delayCycles = args.firstOrNull()?.toIntOrNull()?.coerceIn(6, 12) ?: 8
            val requestedStart = args.getOrNull(1)?.toIntOrNull() ?: 1
            val requestedEnd = args.getOrNull(2)?.toIntOrNull()
            protectedAccess.launch(player) {
                val weaponIds = specialAttacks.registeredItemIds().sorted()
                val startIndex = requestedStart.coerceIn(1, weaponIds.size)
                val endIndex = (requestedEnd ?: weaponIds.size).coerceIn(startIndex, weaponIds.size)
                val primaryType = requireNotNull(ServerCacheManager.getNpc(2215))
                val vampireTypeId = "npc.burgh_vampire_juvenile_1".asRSCM(RSCMType.NPC)
                val vampireType = requireNotNull(ServerCacheManager.getNpc(vampireTypeId))
                val primaryTarget = Npc(primaryType, player.coords.translateX(4))
                val vampireTarget = Npc(vampireType, player.coords.translateX(1))
                for (npc in listOf(primaryTarget, vampireTarget)) {
                    npc.mode = NpcMode.None
                    npc.baseHitpointsLvl = SPECIAL_TARGET_HITPOINTS
                    npc.hitpoints = SPECIAL_TARGET_HITPOINTS
                    npcRepo.add(npc, SPECIAL_TARGET_DURATION_CYCLES)
                }
                primaryTarget.retainerUntil = Int.MAX_VALUE

                val retainerWeapons = buildSet {
                    add("obj.ivandis_flail".asRSCM(RSCMType.OBJ))
                    add("obj.blisterwood_flail".asRSCM(RSCMType.OBJ))
                    add("obj.hallowed_flail".asRSCM(RSCMType.OBJ))
                    for (charges in 1..10) {
                        add("obj.burgh_rod_command_final_$charges".asRSCM(RSCMType.OBJ))
                    }
                }
                val remainingCount = endIndex - startIndex + 1
                val etaMinutes = remainingCount * delayCycles * 0.6 / 60.0
                mes(
                    "Starting live special audit at $startIndex-$endIndex/${weaponIds.size} " +
                        "(${"%.1f".format(etaMinutes)} minutes)."
                )
                logger.info {
                    "SPECIAL_AUDIT start player=${player.username} weapons=${weaponIds.size} " +
                        "delay=$delayCycles start=$startIndex end=$endIndex"
                }

                var accepted = 0
                var rejected = 0
                var errors = 0
                for ((offset, itemId) in
                    weaponIds.slice(startIndex - 1 until endIndex).withIndex()) {
                    val index = startIndex - 1 + offset
                    player.clearInteractionRoute()
                    player.combatClearQueue()
                    resetAnim()
                    val itemType = requireNotNull(ServerCacheManager.getItem(itemId))
                    val weapon = player.specialAuditWeapon(itemType)
                    player.righthand = weapon
                    player.quiver = specialAuditAmmo(itemType)
                    when {
                        "frostmoon_spear" in itemType.internalName -> {
                            player.hat = InvObj("obj.frost_moon_helm")
                            player.torso = InvObj("obj.frost_moon_chestplate")
                            player.legs = InvObj("obj.frost_moon_tassets")
                        }
                        "dual_macuahuitl" in itemType.internalName -> {
                            player.hat = InvObj("obj.blood_moon_helm")
                            player.torso = InvObj("obj.blood_moon_chestplate")
                            player.legs = InvObj("obj.blood_moon_tassets")
                        }
                        "eclipse_atlatl" in itemType.internalName -> {
                            player.hat = InvObj("obj.eclipse_moon_helm")
                            player.torso = InvObj("obj.eclipse_moon_chestplate")
                            player.legs = InvObj("obj.eclipse_moon_tassets")
                        }
                        else -> {
                            player.hat = null
                            player.torso = null
                            player.legs = null
                        }
                    }
                    if ("noxious_halberd" in itemType.internalName) {
                        VarPlayerIntMapSetter.set(player, "varp.poison_severity", 46)
                    }
                    if ("soulreaper" in itemType.internalName) {
                        VarPlayerIntMapSetter.set(player, "varp.soulreaper_stacks", 5)
                    }
                    specialEnergy.restoreFullSpecialEnergy(player)

                    val needsSmallOrAdjacentTarget =
                        itemId in retainerWeapons ||
                            "granite_maul" in itemType.internalName ||
                            "fang_of_the_hound" in itemType.internalName ||
                            "eclipse_atlatl" in itemType.internalName ||
                            "dragon_spear" in itemType.internalName ||
                            "zamorak_spear" in itemType.internalName ||
                            "zamorak_hasta" in itemType.internalName
                    val target = if (needsSmallOrAdjacentTarget) vampireTarget else primaryTarget
                    if (
                        "granite_maul" in itemType.internalName ||
                            "fang_of_the_hound" in itemType.internalName
                    ) {
                        player.interaction =
                            InteractionNpcOp(
                                target = target,
                                op = InteractionOp.Op2,
                                hasOpTrigger = false,
                                hasApTrigger = true,
                            )
                    }
                    // Specials such as Shove move the shared adjacent fixture. Reset both audit
                    // targets before every entry so one weapon cannot invalidate a later test or
                    // hide its animation behind stale collision/position state.
                    primaryTarget.telejump(collision, player.coords.translateX(4))
                    vampireTarget.telejump(collision, player.coords.translateX(1))

                    target.baseHitpointsLvl = SPECIAL_TARGET_HITPOINTS
                    target.hitpoints =
                        if (target === vampireTarget) {
                            SPECIAL_TARGET_HITPOINTS / 2
                        } else {
                            SPECIAL_TARGET_HITPOINTS
                        }
                    val special = requireNotNull(specialAttacks[weapon])
                    val label = "[${index + 1}/${weaponIds.size}] ${itemType.name} (ID $itemId)"
                    mes("Testing $label")
                    logger.info { "SPECIAL_AUDIT testing $label" }

                    val syntheticCondition =
                        when (itemId) {
                            22516 -> "verzik-phase-1-only"
                            else -> null
                        }
                    if (syntheticCondition != null) {
                        rejected++
                        logger.info {
                            "SPECIAL_AUDIT result item=$itemId accepted=false " +
                                "condition=$syntheticCondition energy=${specialEnergy.getSpecialEnergy(player)}"
                        }
                        delay(delayCycles)
                        continue
                    }

                    try {
                        val instantAccepted =
                            when (special) {
                                is SpecialAttack.Instant -> special.activate(this)
                                is SpecialAttack.Melee -> {
                                    val type = MeleeAttackType.from(attackTypes.get(player))
                                    val style = MeleeAttackStyle.from(attackStyles.get(player))
                                    val stance =
                                        CombatStance[player.vars["varp.com_mode"]]
                                            ?: CombatStance.Stance1
                                    if (itemId == 27859) {
                                        special.attack(
                                            this,
                                            player,
                                            CombatAttack.Melee(weapon, type, style, stance),
                                        )
                                    } else
                                        special.attack(
                                            this,
                                            target,
                                            CombatAttack.Melee(weapon, type, style, stance),
                                        )
                                }
                                is SpecialAttack.Ranged -> {
                                    val type = RangedAttackType.from(attackTypes.get(player))
                                    val style = RangedAttackStyle.from(attackStyles.get(player))
                                    special.attack(
                                        this,
                                        target,
                                        CombatAttack.Ranged(weapon, type, style),
                                    )
                                }
                                is SpecialAttack.Magic -> {
                                    val style = MagicAttackStyle.from(attackStyles.get(player))
                                    special.attack(this, target, CombatAttack.Staff(weapon, style))
                                }
                            }
                        // The audit invokes handlers directly. Cancel their normal-combat
                        // re-engagement immediately so the next ordinary attack cannot overwrite
                        // the special animation one cycle later (most visibly with Dark bows).
                        player.clearInteractionRoute()
                        delay(delayCycles)
                        player.clearInteractionRoute()
                        val energyConsumed =
                            specialEnergy.getSpecialEnergy(player) < SpecialAttackEnergy.MAX_ENERGY
                        val didAccept = instantAccepted || energyConsumed
                        if (didAccept) {
                            accepted++
                        } else {
                            rejected++
                        }
                        logger.info {
                            "SPECIAL_AUDIT result item=$itemId accepted=$didAccept " +
                                "energy=${specialEnergy.getSpecialEnergy(player)}"
                        }
                    } catch (t: Throwable) {
                        errors++
                        logger.error(t) { "SPECIAL_AUDIT error item=$itemId name=${itemType.name}" }
                    }
                }

                player.clearInteractionRoute()
                player.combatClearQueue()
                player.righthand = null
                player.quiver = null
                player.hat = null
                player.torso = null
                player.legs = null
                mes(
                    "Special audit complete: $remainingCount tested, $accepted accepted, " +
                        "$rejected condition-rejected, $errors errors."
                )
                logger.info {
                    "SPECIAL_AUDIT complete player=${player.username} tested=$remainingCount " +
                        "accepted=$accepted rejected=$rejected errors=$errors"
                }
            }
        }

    private fun Player.specialAuditWeapon(itemType: ItemServerType): InvObj {
        val weapon = InvObj(itemType, if (itemType.stackable) 10_000 else 1)
        if ("webweaver" in itemType.internalName) {
            val varobj =
                requireNotNull(
                    ServerCacheManager.getVarObj("varobj.charges_16383".asRSCM(RSCMType.VAROBJ))
                )
            return weapon.copy(vars = weapon.vars.withBits(varobj.bits, 10_000))
        }

        if ("blowpipe" !in itemType.internalName) {
            return weapon
        }

        val fixture = Inventory(inv.type, arrayOfNulls(inv.objs.size))
        fixture[0] = weapon
        val dartAlias =
            if (BlowpipeAmmo.isToxic(weapon)) {
                "obj.dragon_dart"
            } else {
                "obj.adamant_dart"
            }
        val dartType = requireNotNull(ServerCacheManager.getItem(dartAlias.asRSCM(RSCMType.OBJ)))
        check(BlowpipeAmmo.storeDarts(fixture, 0, dartType, 10_000) == 10_000) {
            "Unable to load $dartAlias into ${itemType.internalName}"
        }
        if (BlowpipeAmmo.isToxic(fixture[0])) {
            check(BlowpipeAmmo.storeScales(fixture, 0, 10_000) == 10_000) {
                "Unable to load scales into ${itemType.internalName}"
            }
        }
        return requireNotNull(fixture[0])
    }

    private fun specialAuditAmmo(itemType: ItemServerType): InvObj? =
        when {
            "dttd_bone_crossbow" in itemType.internalName ->
                InvObj("obj.dttd_bone_crossbow_bolt", 10_000)
            "atlatl" in itemType.internalName -> InvObj("obj.atlatl_dart", 10_000)
            itemType.isCategoryType("category.ballista") -> InvObj("obj.dragon_javelin", 10_000)
            itemType.isCategoryType("category.crossbow") ||
                "crossbow" in itemType.internalName ||
                "crossbow" in itemType.name.lowercase() -> InvObj("obj.dragon_bolts", 10_000)
            itemType.isCategoryType("category.bow") -> InvObj("obj.rune_arrow", 10_000)
            else -> null
        }

    private fun setVarp(cheat: Cheat) =
        with(cheat) {
            val typeId = "varp.${args[0]}".asRSCM()

            val type = ServerCacheManager.getVarp(typeId)
            if (type == null) {
                player.mes("That varp does not exist: $typeId")
                return
            }
            val value = args[1].toInt()
            player.vars.backing[type.id] = value
            player.resyncVar(type)
            player.mes("Set varp '${args[0]}' to value: ${player.vars[type]}")
        }

    private fun setVarBit(cheat: Cheat) =
        with(cheat) {
            val typeId = "varbit.${args[0]}".asRSCM()

            val type = ServerCacheManager.getVarbit(typeId)
            if (type == null) {
                player.mes("That varbit does not exist: $typeId")
                return
            }
            val value = args[1].toInt()
            VarPlayerIntMapSetter.set(player, type, value)
            player.mes("Set varbit '${args[0]}' to value: ${player.vars[type]}")
        }

    private fun getVarp(cheat: Cheat) =
        with(cheat) {
            val typeId = "varp.${args[0]}".asRSCM()

            val type = ServerCacheManager.getVarp(typeId)
            if (type == null) {
                player.mes("That varp does not exist: $typeId")
                return
            }
            player.mes("Varp '${args[0]}' (id=$typeId) = ${player.vars[type]}")
        }

    private fun getVarBit(cheat: Cheat) =
        with(cheat) {
            val typeId = "varbit.${args[0]}".asRSCM()

            val type = ServerCacheManager.getVarbit(typeId)
            if (type == null) {
                player.mes("That varbit does not exist: $typeId")
                return
            }
            player.mes("Varbit '${args[0]}' (id=$typeId) = ${player.vars[type]}")
        }

    @OptIn(InternalApi::class)
    private fun Player.setStatLevels(level: Int) {
        val xp = PlayerSkillXPTable.getXPFromLevel(level)
        for (stat in ServerCacheManager.getStats().values) {
            val statInternal = RSCM.getReverseMapping(RSCMType.STAT, stat.id)

            val baseLevel = statMap.getBaseLevel(statInternal)
            val targetLevel = max(stat.minLevel, level)
            if (baseLevel > targetLevel) {
                statRevert(statInternal, targetLevel, xp)
                continue
            }
            val xpDelta = xp - statMap.getXP(statInternal)
            statMap.setCurrentLevel(statInternal, targetLevel.toByte())
            statAdvance(statInternal, xpDelta.toDouble(), rate = 1.0)
        }
    }

    // There is, by design, no helper function to decrease stat xp, as xp reduction is not a
    // standard operation in normal gameplay.
    @OptIn(InternalApi::class)
    private fun Player.statRevert(stat: String, targetLevel: Int, targetXp: Int) {
        statMap.setCurrentLevel(stat, statMap.getBaseLevel(stat))
        val levelDelta = stat(stat) - targetLevel
        require(levelDelta > 0) { "This function can only be used to reduce stat levels." }
        statMap.setXP(stat, targetXp)
        statMap.setBaseLevel(stat, targetLevel.toByte())
        statSub(stat, constant = levelDelta, percent = 0)
        appearance.combatLevel = PlayerSkillXP.calculateCombatLevel(this)
        PlayerInterfaceUpdates.updateCombatLevel(this)
    }

    private fun reboot(cheat: Cheat) {
        logger.info { "Reboot initiated by '${cheat.player.displayName}'." }
        SafeServiceExit.terminate()
    }

    private fun slowReboot(cheat: Cheat) =
        with(cheat) {
            val cycles = min(args[0].toInt(), 65535)
            if (cycles <= 0) {
                update.clear()
                return@with
            }
            update.startCountdown(cycles)
            for (p in playerList) {
                MiscOutput.updateRebootTimer(p, cycles)
            }
        }

    private fun dieTest(cheat: Cheat) =
        with(cheat) {
            val mode = args.getOrNull(0)?.lowercase()
            val inWildy = args.getOrNull(1)?.lowercase() == "true"
            when (mode) {
                "pvm" -> {
                    if (inWildy) player.insideWilderness = true
                    player.mes("Simulating PvM death${if (inWildy) " in Wilderness" else ""}.")
                    player.prepareAdminDieTest()
                    player.queueDeath()
                }
                "pvp" -> {
                    player.preparePvpDeath(player)
                    if (inWildy) player.insideWilderness = true
                    player.mes(
                        "Simulating PvP death${if (inWildy) " in Wilderness" else ""}. (self as killer)"
                    )
                    player.prepareAdminDieTest()
                    player.queueDeath()
                }
                else -> player.mes("Usage: ::die pvm|pvp [true|false]  (true = in Wilderness)")
            }
        }

    private fun bank(cheat: Cheat) =
        with(cheat) {
            protectedAccess.launch(player) {
                ifOpenMainSidePair(main = "interface.bankmain", side = "interface.bankside")
            }
        }

    private fun transmog(cheat: Cheat) =
        with(cheat) {
            if (args.isEmpty()) {
                protectedAccess.launch(player) { resetTransmog() }
                player.mes("Transmog cleared.")
                return
            }
            val first = args[0]
            val npcName =
                if (first.toIntOrNull() != null) {
                    val resolved = RSCM.getReverseMapping(RSCMType.NPC, first.toInt())
                    if (resolved.isEmpty()) {
                        player.mes("No NPC mapped to ID: $first")
                        return
                    }
                    resolved
                } else {
                    "npc.${args.asTypeName()}"
                }
            protectedAccess.launch(player) { transmog(npcName) }
            player.mes("Transmog: '$npcName'")
        }

    private fun instanceExit(cheat: Cheat) =
        with(cheat) {
            val key = args.getOrNull(0)?.trim()
            if (key.isNullOrEmpty()) {
                player.mes("Usage: ::instanceexit instanceKey")
                player.mes("Known keys: ${instanceRegistry.keys().sorted().joinToString(", ")}")
                return@with
            }
            val spec = instanceRegistry.get(key)
            if (spec == null) {
                player.mes("No instance found with key: '$key'")
                player.mes("Known keys: ${instanceRegistry.keys().sorted().joinToString(", ")}")
                return@with
            }
            val exit = spec.area.exitCoord()
            if (exit == null || exit == CoordGrid.ZERO) {
                player.mes("Instance '$key' has no exit coord configured.")
                return@with
            }
            protectedAccess.launch(player) {
                player.mes("Teleported to '$key' instance exit coord: $exit")
                instances.leaveForExternalTeleport(player, exit, clock.cycle)
                telejump(exit, TeleportType.Exempt)
            }
        }

    private fun InstanceArea.exitCoord(): CoordGrid? =
        when (this) {
            is InstanceArea.Fixed -> exitCoord
            is InstanceArea.Template -> exitCoord
            is InstanceArea.CopyRegions -> exitCoord
        }

    private fun resolveArgTypeId(arg: String, names: Map<String, Int>): Int? {
        val argAsInt = arg.toIntOrNull()
        if (argAsInt != null) {
            return argAsInt
        }
        val sanitized = arg.replace("-", "_")
        return names[sanitized]
    }

    private fun resolveTypeName(name: String, names: Map<String, Int>): String =
        when {
            name in names -> name
            name.toIntOrNull() != null -> name
            else -> findClosestNameMatch(name, names.keys) ?: name
        }

    private fun List<String>.asTypeNameAndNumber(defaultNumber: Number): Pair<String, String> =
        if (size > 1 && last().toLongOrNull() != null) {
            dropLast(1).joinToString("_") to last()
        } else {
            joinToString("_") to defaultNumber.toString()
        }

    private fun List<String>.asTypeName(): String = joinToString("_")

    private fun findClosestNameMatch(input: String, names: Iterable<String>): String? {
        val normalizedInput = input.replace("_", " ")

        var bestMatchScore = 0.0f
        var bestMatchName: String? = null
        for (name in names) {
            val score = levenshteinMetric.compare(normalizedInput, name.replace("_", " "))
            if (score > bestMatchScore) {
                bestMatchScore = score
                bestMatchName = name
            }
        }

        return if (bestMatchScore >= 0.5) bestMatchName else null
    }
}
