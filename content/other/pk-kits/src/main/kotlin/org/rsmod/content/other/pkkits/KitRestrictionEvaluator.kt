package org.rsmod.content.other.pkkits

public fun evaluateKitRestrictions(context: KitRestrictionContext): List<String> = buildList {
    if (!context.mutableOnline)
        add("Kits cannot be changed while your account is logging out or shutting down.")
    if (!context.alive) add("Kits cannot be changed while you are dead.")
    if (context.inCombat) add("You must be out of combat before changing kits.")
    if (context.inWilderness) add("Kits cannot be changed in the Wilderness.")
    if (context.inInstance) add("Kits cannot be changed inside an instance or minigame.")
    if (context.busy) add("Finish your current action before changing kits.")
    if (context.ultimateIronman) add("Ultimate Ironman accounts cannot use bank-backed kits.")
    if (!context.permittedArea) add("You must be in a permitted safe area before changing kits.")
}
