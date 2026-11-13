package io.github.luannamorim.myfinance.core.format

/**
 * Cash-register style amount entry: the user types digits and they fill in from the cents
 * ("3", "31", "312", "3124" → 0,03 · 0,31 · 3,12 · 31,24). The state is the digit string only,
 * so there is no decimal separator to get wrong on any keyboard.
 */
object AmountInput {
    const val MAX_DIGITS = 13

    /** Minor units for [digits]; empty is zero; anything but digits, or more than [MAX_DIGITS], is null. */
    fun minorOf(digits: String): Long? {
        if (digits.length > MAX_DIGITS || digits.any { it !in '0'..'9' }) return null
        return if (digits.isEmpty()) 0 else digits.toLong()
    }

    /** Digit string for an amount (no leading zeros; zero is empty). */
    fun digitsOf(minor: Long): String {
        require(minor >= 0) { "Amount input is unsigned" }
        return if (minor == 0L) "" else minor.toString()
    }

    /** Keeps only digits, drops leading zeros and caps the length (for pasted or IME text). */
    fun sanitize(text: String): String = text.filter { it in '0'..'9' }.trimStart('0').take(MAX_DIGITS)
}
