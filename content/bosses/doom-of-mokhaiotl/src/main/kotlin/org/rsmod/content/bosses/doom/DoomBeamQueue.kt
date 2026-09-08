package org.rsmod.content.bosses.doom

import org.rsmod.game.queue.PlayerQueueList

/** Identity-safe access to the one queued beam hit. */
internal object DoomBeamQueue {
    fun containsIdentity(queues: PlayerQueueList, expected: Any): Boolean {
        val iterator = queues.iterator() ?: return false
        var found = false
        try {
            while (iterator.hasNext()) {
                if (matchesIdentity(iterator.next().args, expected)) {
                    found = true
                    break
                }
            }
        } finally {
            iterator.cleanUp()
        }
        return found
    }

    fun removeIdentity(queues: PlayerQueueList, expected: Any): Boolean {
        val iterator = queues.iterator() ?: return false
        var removed = false
        try {
            while (iterator.hasNext()) {
                if (matchesIdentity(iterator.next().args, expected)) {
                    iterator.remove()
                    removed = true
                    break
                }
            }
        } finally {
            iterator.cleanUp()
        }
        return removed
    }

    fun matchesIdentity(candidate: Any?, expected: Any): Boolean = candidate === expected
}
