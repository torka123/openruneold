package dev.openrune.cache.filestore.definition;

import dev.openrune.definition.type.widget.ComponentType;
import java.util.Map;

/** Java bridge for the dependency's Kotlin-internal InterfaceType constructor. */
public final class InterfaceTypeFactory {
    private InterfaceTypeFactory() {}

    public static InterfaceType create(
            Map<Integer, ComponentType> components, int id, String internalName) {
        return new InterfaceType(components, id, internalName);
    }
}
