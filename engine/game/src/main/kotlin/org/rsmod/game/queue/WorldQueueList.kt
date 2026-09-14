package org.rsmod.game.queue

public class WorldQueueList {
    public var size: Int = 0
        private set

    private val iterator = QueueIterator()

    private var first: Node? = null
    private var last: Node? = null

    public val isEmpty: Boolean
        get() = size == 0

    public val isNotEmpty: Boolean
        get() = size > 0

    public fun add(remainingCycles: Int, action: () -> Unit) {
        addCancellable(remainingCycles, action)
    }

    /** Enqueues on the normal world list with explicit, game-thread-owned cancellation. */
    public fun addCancellable(remainingCycles: Int, action: () -> Unit): Cancellation =
        addCancellable(remainingCycles, ready = { true }, action = action)

    public fun addCancellable(
        remainingCycles: Int,
        ready: () -> Boolean,
        action: () -> Unit,
    ): Cancellation = enqueue(remainingCycles, ready, action, owner = null)

    private fun enqueue(
        remainingCycles: Int,
        ready: () -> Boolean,
        action: () -> Unit,
        owner: Owner?,
    ): Cancellation {
        val queue = Queue(action, remainingCycles, ready, owner)
        val node = Node(queue, prev = last)
        add(node)
        return Cancellation { remove(node) }
    }

    /**
     * Owns entries on this same native list for explicit dispatch at an established game-thread
     * boundary. Normal world processing does not advance these entries. Content must close the
     * owner from its native session/encounter cleanup.
     */
    public fun createOwner(): Owner = Owner(this)

    public class Owner internal constructor(internal val queues: WorldQueueList) {
        public var revision: Long = 0
            private set

        public var isClosed: Boolean = false
            private set

        public val size: Int
            get() = queues.ownedNodes(this).size

        public fun add(
            remainingCycles: Int = 0,
            ready: () -> Boolean = { true },
            action: () -> Unit,
        ): Cancellation {
            check(!isClosed) { "World queue owner is closed." }
            return queues.enqueue(remainingCycles, ready, action, this)
        }

        /** Cancels pending entries and invalidates effects captured before this reset. */
        public fun clear() {
            if (isClosed) return
            revision++
            queues.ownedNodes(this).forEach(queues::remove)
        }

        public fun close() {
            if (isClosed) return
            clear()
            isClosed = true
        }
    }

    /**
     * Used by the native processor for an owner's due batch. Keep nodes linked until their turn:
     * clearing or cancelling from an earlier callback must still suppress a later due callback.
     * Snapshot before evaluating readiness so additions during this dispatch wait for the next one.
     */
    public fun dispatchOwned(owner: Owner, ready: (Queue) -> Boolean) {
        require(owner.queues === this) { "World queue owner belongs to another list." }
        val due = ownedNodes(owner).filter { it.linked && ready(it.queue) }
        for (node in due) {
            if (!node.linked) continue
            remove(node)
            node.queue.action()
        }
    }

    private fun ownedNodes(owner: Owner): List<Node> = buildList {
        var node = first
        while (node != null) {
            if (node.queue.owner === owner) add(node)
            node = node.next
        }
    }

    public class Cancellation internal constructor(private var cancelAction: (() -> Unit)?) {
        public fun cancel() {
            val action = cancelAction ?: return
            cancelAction = null
            action()
        }
    }

    private fun add(node: Node) {
        if (isEmpty) {
            check(first == null)
            check(last == null)
            first = node
        }
        last?.next = node
        last = node
        size++
    }

    private fun remove(node: Node) {
        if (!node.linked) return
        node.linked = false
        iterator.onRemoved(node)
        val prev = node.prev
        val next = node.next

        // If `prev` is null that means `node` was the first node.
        if (prev == null) {
            first = next
        } else {
            prev.next = next
        }

        // If `next` is null that means `node` was the last node.
        if (next == null) {
            last = prev
        } else {
            next.prev = prev
        }

        check(size > 0)
        size--
        node.prev = null
        node.next = null
    }

    override fun toString(): String = "WorldQueueList(size=$size, first=$first, last=$last)"

    public data class Node(val queue: Queue, var prev: Node?, var next: Node? = null) {
        internal var linked: Boolean = true
    }

    public data class Queue(
        val action: () -> Unit,
        var remainingCycles: Int,
        val ready: () -> Boolean = { true },
        val owner: Owner? = null,
    )

    public fun iterator(includeOwned: Boolean = true): QueueIterator {
        iterator.reset(first, includeOwned)
        return iterator
    }

    // We are assuming that world queues have the same "speed-up" mechanic as player and npc
    // queues.
    public inner class QueueIterator {
        private var next: Node? = null
        private var curr: Node? = null
        private var includeOwned: Boolean = true

        public fun hasNext(): Boolean = next != null

        public fun next(): Queue {
            val node = next ?: throw NoSuchElementException()
            curr = node
            next = eligible(node.next)
            return node.queue
        }

        public fun remove() {
            val node = curr ?: error("`next` must be called before any `remove`.")
            remove(node)
        }

        public fun cleanUp() {
            this.next = null
            this.curr = null
        }

        internal fun onRemoved(node: Node) {
            if (next === node) next = eligible(node.next)
            if (curr === node) curr = null
        }

        internal fun reset(first: Node?, includeOwned: Boolean) {
            this.includeOwned = includeOwned
            this.next = eligible(first)
            this.curr = null
        }

        private fun eligible(start: Node?): Node? {
            var node = start
            while (!includeOwned && node != null && node.queue.owner != null) node = node.next
            return node
        }
    }
}
