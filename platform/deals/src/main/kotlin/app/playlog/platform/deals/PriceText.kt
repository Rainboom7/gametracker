package app.playlog.platform.deals

/** Numbers out of store price strings in any locale: "1 799,00 TL", "$19.99", "₹1,999", "1.299,00 zł". */
internal object PriceText {
    private val NUMBER = Regex("\\d[\\d.,\\s\\u00A0\\u202F]*")
    private const val MAX_DECIMALS = 2

    fun value(text: String?): Double? {
        val raw = text?.let { NUMBER.find(it)?.value } ?: return null
        val compact = raw.filter { it.isDigit() || it == '.' || it == ',' }.trimEnd('.', ',')
        val last = compact.indexOfLast { it == '.' || it == ',' }
        val decimals = if (last < 0) 0 else compact.length - last - 1
        val normalized = if (last >= 0 && decimals in 1..MAX_DECIMALS) {
            compact.substring(0, last).filter(Char::isDigit) + "." + compact.substring(last + 1)
        } else {
            compact.filter(Char::isDigit)
        }
        return normalized.toDoubleOrNull()
    }
}
