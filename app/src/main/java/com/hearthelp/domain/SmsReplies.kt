package com.hearthelp.domain

object SmsReplies {
    private val ok = Regex("""\b(ok|okay)\b""", RegexOption.IGNORE_CASE)
    fun isOk(body: String): Boolean = ok.containsMatchIn(body)
}
