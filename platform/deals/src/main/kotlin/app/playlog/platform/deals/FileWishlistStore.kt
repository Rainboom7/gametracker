package app.playlog.platform.deals

import app.playlog.core.data.WishlistStore
import app.playlog.core.model.Platform
import app.playlog.core.model.StorePrice
import app.playlog.core.model.WishlistItem
import app.playlog.core.network.TrackerJson
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.time.Instant

/** The wishlist as one JSON file in the app's files dir (user data, not cache). */
class FileWishlistStore(
    private val file: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : WishlistStore {
    private val serializer = ListSerializer(StoredWish.serializer())

    override suspend fun read(): List<WishlistItem> = withContext(io) {
        if (!file.exists()) return@withContext emptyList()
        runCatching { TrackerJson.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyList()).map { it.toModel() }
    }

    override suspend fun write(items: List<WishlistItem>) = withContext(io) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(TrackerJson.encodeToString(serializer, items.map(StoredWish::of)))
        if (!temp.renameTo(file)) {
            temp.copyTo(file, overwrite = true)
            temp.delete()
        }
        Unit
    }
}

@Serializable
internal data class StoredWish(
    val platform: Platform,
    val storeId: String,
    val title: String,
    val header: String,
    val storeUrl: String,
    val addedAt: Long,
    val price: StoredPrice? = null,
    val checkedAt: Long? = null,
    val notifiedDiscount: Int = 0,
) {
    fun toModel() = WishlistItem(
        platform, storeId, title, header, storeUrl, Instant.ofEpochMilli(addedAt), price?.toModel(),
        checkedAt?.let(Instant::ofEpochMilli), notifiedDiscount,
    )

    companion object {
        fun of(w: WishlistItem) = StoredWish(
            w.platform, w.storeId, w.title, w.headerUrl, w.storeUrl, w.addedAt.toEpochMilli(), w.price?.let(StoredPrice::of),
            w.checkedAt?.toEpochMilli(), w.notifiedDiscount,
        )
    }
}

@Serializable
internal data class StoredPrice(
    val text: String,
    val original: String? = null,
    val discount: Int = 0,
    val value: Double? = null,
    val endsAt: Long? = null,
) {
    fun toModel() = StorePrice(text, original, discount, value, endsAt?.let(Instant::ofEpochMilli))

    companion object {
        fun of(p: StorePrice) = StoredPrice(p.priceText, p.originalPriceText, p.discountPercent, p.priceValue, p.endsAt?.toEpochMilli())
    }
}
