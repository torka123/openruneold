package org.rsmod.game.damage

import kotlin.math.min
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.npc.NpcUid
import org.rsmod.game.hit.HitOrigin

public class DamageContributions {
    private val entries: LinkedHashMap<DamageContributorKey, DamageContributor> = linkedMapOf()
    private val origins = mutableMapOf<DamageContributorKey, MutableMap<HitOrigin, Int>>()

    public val isEmpty: Boolean
        get() = entries.isEmpty()

    public val size: Int
        get() = entries.size

    public fun clear() {
        entries.clear()
        origins.clear()
    }

    public fun record(source: Player, damage: Int, origin: HitOrigin = HitOrigin.Direct) {
        if (damage <= 0) {
            return
        }
        val uuid = source.uuid ?: error("Unexpected null uuid for player: $source")
        recordPlayer(uuid, damage, origin)
    }

    public fun record(source: Npc, damage: Int, origin: HitOrigin = HitOrigin.Direct) {
        if (damage <= 0) {
            return
        }
        recordNpc(source.uid, damage, origin)
    }

    public fun damageBy(source: Player): Int {
        val uuid = source.uuid ?: return 0
        return (entries[DamageContributorKey.Player(uuid)] as? DamageContributor.ByPlayer)?.damage
            ?: 0
    }

    public fun damageBy(source: Npc): Int =
        (entries[DamageContributorKey.Npc(source.uid)] as? DamageContributor.ByNpc)?.damage ?: 0

    public fun damageBy(source: Player, origin: HitOrigin): Int =
        source.uuid?.let { damageBy(it, origin) } ?: 0

    public fun damageBy(ownerUuid: Long, origin: HitOrigin): Int =
        origins[DamageContributorKey.Player(ownerUuid)]?.get(origin) ?: 0

    public fun damageBy(source: Npc, origin: HitOrigin): Int =
        origins[DamageContributorKey.Npc(source.uid)]?.get(origin) ?: 0

    public fun entries(): List<DamageContributor> = entries.values.toList()

    public fun sortedByDamageDescending(): List<DamageContributor> =
        entries.values.sortedByDescending { it.damage }

    public fun sortedByDamageAscending(): List<DamageContributor> =
        entries.values.sortedBy { it.damage }

    public fun mostDamage(): DamageContributor? = entries.values.maxByOrNull { it.damage }

    public fun leastDamage(): DamageContributor? = entries.values.minByOrNull { it.damage }

    public fun totalDamage(): Int =
        entries.values.sumOf { it.damage.toLong() }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    public fun absorbFrom(other: DamageContributions) {
        for (contributor in other.entries().filterIsInstance<DamageContributor.ByPlayer>()) {
            val attributed = other.origins[DamageContributorKey.Player(contributor.uuid)].orEmpty()
            for ((origin, damage) in attributed) recordPlayer(contributor.uuid, damage, origin)
        }
    }

    public fun topPlayer(): DamageContributor.ByPlayer? =
        mostDamage() as? DamageContributor.ByPlayer

    public fun leastPlayer(): DamageContributor.ByPlayer? {
        val players = entries.values.filterIsInstance<DamageContributor.ByPlayer>()
        return players.minByOrNull { it.damage }
    }

    public fun topNpc(): DamageContributor.ByNpc? = mostDamage() as? DamageContributor.ByNpc

    public fun leastNpc(): DamageContributor.ByNpc? {
        val npcs = entries.values.filterIsInstance<DamageContributor.ByNpc>()
        return npcs.minByOrNull { it.damage }
    }

    public fun topPlayer(playerList: PlayerList): Player? = topPlayer()?.resolve(playerList)

    public fun leastPlayer(playerList: PlayerList): Player? = leastPlayer()?.resolve(playerList)

    public fun topNpc(npcList: NpcList): Npc? = topNpc()?.resolve(npcList)

    public fun leastNpc(npcList: NpcList): Npc? = leastNpc()?.resolve(npcList)

    public fun recordPlayer(uuid: Long, damage: Int, origin: HitOrigin = HitOrigin.Direct) {
        if (damage <= 0) return
        val key = DamageContributorKey.Player(uuid)
        val existing = entries[key] as? DamageContributor.ByPlayer
        val accepted = min(damage, Int.MAX_VALUE - (existing?.damage ?: 0))
        recordOrigin(key, origin, accepted)
        if (existing != null) {
            existing.damage = min(Int.MAX_VALUE.toLong(), existing.damage + damage.toLong()).toInt()
            return
        }
        entries[key] = DamageContributor.ByPlayer(uuid, damage)
    }

    private fun recordNpc(uid: NpcUid, damage: Int, origin: HitOrigin) {
        val key = DamageContributorKey.Npc(uid)
        val existing = entries[key] as? DamageContributor.ByNpc
        val accepted = min(damage, Int.MAX_VALUE - (existing?.damage ?: 0))
        recordOrigin(key, origin, accepted)
        if (existing != null) {
            existing.damage = min(Int.MAX_VALUE.toLong(), existing.damage + damage.toLong()).toInt()
            return
        }
        entries[key] = DamageContributor.ByNpc(uid, damage)
    }

    private fun recordOrigin(key: DamageContributorKey, origin: HitOrigin, damage: Int) {
        if (damage <= 0) return
        val values = origins.getOrPut(key) { mutableMapOf() }
        values[origin] = (values[origin] ?: 0) + damage
    }
}
