package org.rsmod.api.net.rsprot.provider

import com.github.michaelbull.logging.InlineLogger
import java.lang.Exception
import net.rsprot.protocol.api.suppliers.WorldEntityInfoSupplier
import net.rsprot.protocol.game.outgoing.info.worldentityinfo.WorldEntityAvatarExceptionHandler

object WorldEntityProvider {
    private val logger = InlineLogger()

    fun provide(): WorldEntityInfoSupplier {
        return WorldEntityInfoSupplier(ExceptionHandler)
    }

    private object ExceptionHandler : WorldEntityAvatarExceptionHandler {
        override fun exceptionCaught(index: Int, exception: Exception) {
            logger.error(exception) { "World-entity protocol failure at client slot $index." }
        }
    }
}
