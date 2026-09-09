package org.rsmod.game.queue

/** Permanent retirement only, not temporary conditions that can recover before the due tick. */
public interface QueueArgumentValidity {
    public fun isQueueArgumentValid(): Boolean
}
