package com.hearthelp.domain

object SmsReplies {
    private val startsWithOk = Regex("""^\s*(ok|okay)\b""", RegexOption.IGNORE_CASE)
    fun isOk(body: String): Boolean = startsWithOk.containsMatchIn(body) && '?' !in body
}
