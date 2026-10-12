package com.hearthelp.domain

object PhoneNumbers {
    fun digits(number: String): String = number.filter(Char::isDigit)

    /** True when both numbers end in the same (up to) 10 digits, so "+91 98765 43210" matches "09876543210". */
    fun same(a: String, b: String): Boolean {
        val x = digits(a)
        val y = digits(b)
        if (x.length < 7 || y.length < 7) return false
        val k = minOf(x.length, y.length, 10)
        return x.takeLast(k) == y.takeLast(k)
    }
}
