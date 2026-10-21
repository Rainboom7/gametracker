package app.playlog.platform.deals

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class DealsException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** `cheapshark.com/api/1.0/deals`: numbers arrive as strings. */
@Serializable
internal data class CheapSharkDealDto(
    val title: String = "",
    val steamAppID: String? = null,
    val salePrice: String? = null,
    val normalPrice: String? = null,
    val savings: String? = null,
    val steamRatingPercent: String? = null,
    val dealRating: String? = null,
    val thumb: String? = null,
)

/** `store.steampowered.com/api/featuredcategories`; prices in the smallest currency unit. */
@Serializable
internal data class FeaturedCategoriesDto(val specials: FeaturedCategoryDto? = null)

@Serializable
internal data class FeaturedCategoryDto(val items: List<FeaturedItemDto> = emptyList())

@Serializable
internal data class FeaturedItemDto(
    val id: Long,
    val name: String = "",
    @SerialName("discount_percent") val discountPercent: Int = 0,
    @SerialName("header_image") val headerImage: String? = null,
)

/** `store.steampowered.com/api/storesearch`. */
@Serializable
internal data class StoreSearchDto(val items: List<StoreSearchItemDto> = emptyList())

@Serializable
internal data class StoreSearchItemDto(val id: Long, val name: String = "", val type: String? = null)

/** `store.steampowered.com/api/appdetails` is keyed by appid. */
@Serializable
internal data class ShopDetailsEnvelope(val success: Boolean = false, val data: ShopDetailsDto? = null)

@Serializable
internal data class ShopDetailsDto(
    val name: String? = null,
    val type: String? = null,
    @SerialName("short_description") val shortDescription: String? = null,
    @SerialName("header_image") val headerImage: String? = null,
    @SerialName("price_overview") val price: PriceOverviewDto? = null,
    val screenshots: List<ScreenshotDto> = emptyList(),
)

@Serializable
internal data class PriceOverviewDto(
    @SerialName("discount_percent") val discountPercent: Int = 0,
    @SerialName("initial_formatted") val initialFormatted: String? = null,
    @SerialName("final_formatted") val finalFormatted: String? = null,
)

@Serializable
internal data class ScreenshotDto(
    @SerialName("path_thumbnail") val thumbnail: String? = null,
    @SerialName("path_full") val full: String? = null,
)
