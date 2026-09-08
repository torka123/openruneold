package org.rsmod.tools.wiki.dumping.wiki

import com.fasterxml.jackson.databind.ObjectMapper
import dev.openrune.wiki.WikiDumpStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import java.io.Closeable
import java.io.File
import java.nio.file.Files
import kotlin.io.path.Path

/** Reads OSRS Wiki page wikitext from a local XML dump or the live MediaWiki API. */
class WikiClient
private constructor(
    private val store: WikiDumpStore?,
    private val liveClient: HttpClient?,
    private val mapper: ObjectMapper?,
    private val onPageFetch: ((String) -> Unit)? = null,
) : Closeable {
    fun wikiDumpStore(): WikiDumpStore =
        requireNotNull(store) { "This operation requires a local Wiki dump." }

    private val pageCache = mutableMapOf<String, String>()

    val loadedPages: Int
        get() = store?.pageCount ?: 0

    val infoboxMonsterPages: Int
        get() = store?.infoboxMonsterCount ?: 0

    val itemSpawnPageCount: Int
        get() = if (store == null) 0 else cachedItemSpawnTitles().size

    val cachedPages: Int
        get() = pageCache.size

    private var itemSpawnTitlesCache: List<String>? = null

    override fun close() {
        liveClient?.close()
    }

    suspend fun rawPageSource(title: String): String {
        pageCache[title]?.let {
            return it
        }
        onPageFetch?.invoke(title)
        val content = store?.rawPageSource(title) ?: fetchLivePageSource(title)
        pageCache[title] = content
        return content
    }

    private suspend fun fetchLivePageSource(title: String): String {
        val client =
            requireNotNull(liveClient) { "No local Wiki dump or live API client is available." }
        val json =
            client
                .get(API_BASE_URL) {
                    parameter("action", "query")
                    parameter("prop", "revisions")
                    parameter("rvprop", "content")
                    parameter("rvslots", "main")
                    parameter("titles", title)
                    parameter("format", "json")
                    parameter("formatversion", "2")
                    parameter("utf8", "1")
                }
                .bodyAsText()
        val root = requireNotNull(mapper).readTree(json)
        val error = root.path("error")
        if (!error.isMissingNode && !error.isNull) {
            error(error.path("info").asText("Wiki page lookup error"))
        }
        val page = root.path("query").path("pages").firstOrNull()
        if (page == null || page.path("missing").asBoolean(false)) {
            error("The Wiki page '$title' does not exist.")
        }
        val revision = page.path("revisions").firstOrNull()
        val slot = revision?.path("slots")?.path("main")?.path("content")?.asText("").orEmpty()
        val legacy = revision?.path("content")?.asText("").orEmpty()
        return (slot.ifBlank { legacy }).ifBlank {
            error("No wikitext source is available for '$title'.")
        }
    }

    suspend fun fetchItemSpawnPageBatch(batchSize: Int, continueToken: String?): MonsterPageBatch {
        val all = cachedItemSpawnTitles()
        val offset = continueToken?.toIntOrNull() ?: 0
        val titles = all.drop(offset).take(batchSize.coerceIn(1, 500))
        val nextOffset = offset + titles.size
        val hasMore = nextOffset < all.size
        return MonsterPageBatch(
            titles = titles,
            continueToken = if (hasMore) nextOffset.toString() else null,
        )
    }

    suspend fun fetchMonsterPageBatch(batchSize: Int, continueToken: String?): MonsterPageBatch {
        val localStore =
            requireNotNull(store) { "Monster page enumeration requires a local Wiki dump." }
        val offset = continueToken?.toIntOrNull() ?: 0
        val titles =
            localStore.listInfoboxMonsterTitles(offset = offset, limit = batchSize.coerceIn(1, 500))
        val nextOffset = offset + titles.size
        val hasMore = nextOffset < localStore.infoboxMonsterCount
        return MonsterPageBatch(
            titles = titles,
            continueToken = if (hasMore) nextOffset.toString() else null,
        )
    }

    data class MonsterPageBatch(val titles: List<String>, val continueToken: String?)

    private fun cachedItemSpawnTitles(): List<String> {
        itemSpawnTitlesCache?.let {
            return it
        }
        val titles = WikiDumpStorePages.listItemSpawnLineTitles(wikiDumpStore())
        itemSpawnTitlesCache = titles
        return titles
    }

    companion object {
        private const val DEFAULT_DUMP_DIR = "D:/OpenRune/OpenRune-FileStore-Server/dumps/wiki"
        private const val API_BASE_URL = "https://oldschool.runescape.wiki/api.php"

        fun resolveDumpDirectory(flagValue: String?): File {
            val configured =
                flagValue?.trim()?.takeIf { it.isNotEmpty() }
                    ?: System.getenv("WIKI_DUMP_DIR")?.trim()?.takeIf { it.isNotEmpty() }
                    ?: DEFAULT_DUMP_DIR
            return Path(configured).toFile()
        }

        fun open(wikiDumpDir: String? = null, onPageFetch: ((String) -> Unit)? = null): WikiClient {
            val directory = resolveDumpDirectory(wikiDumpDir)
            if (Files.isDirectory(directory.toPath())) {
                return WikiClient(WikiDumpStore.load(directory), null, null, onPageFetch)
            }
            val client =
                HttpClient(CIO) {
                    install(HttpTimeout) {
                        requestTimeoutMillis = 30_000
                        connectTimeoutMillis = 10_000
                        socketTimeoutMillis = 30_000
                    }
                }
            return WikiClient(null, client, ObjectMapper(), onPageFetch)
        }
    }
}
