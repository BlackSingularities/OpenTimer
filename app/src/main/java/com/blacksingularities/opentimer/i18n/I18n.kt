package com.blacksingularities.opentimer.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/** Język interfejsu wybrany w ustawieniach. */
enum class AppLanguage { SYSTEM, PL, EN }

/**
 * Lokalizacja: teksty w kodzie są po polsku (klucze), a [En] zawiera ich angielskie odpowiedniki.
 * Szablony {0}, {1}… są zastępowane argumentami. Odczyt języka to stan Compose – zmiana języka
 * od razu odświeża cały interfejs.
 */
object I18n {
    var english by mutableStateOf(false)
        private set

    val locale: Locale get() = if (english) Locale.ENGLISH else Locale.forLanguageTag("pl-PL")

    fun apply(language: AppLanguage, systemLanguage: String = Locale.getDefault().language) {
        english = when (language) {
            AppLanguage.PL -> false
            AppLanguage.EN -> true
            AppLanguage.SYSTEM -> systemLanguage != "pl"
        }
    }
}

fun tr(pl: String, vararg args: Any?): String {
    val text = if (I18n.english) En[pl] ?: pl else pl
    if (args.isEmpty()) return text
    var out = text
    args.forEachIndexed { i, a -> out = out.replace("{$i}", a?.toString() ?: "") }
    return out
}

/** Polska odmiana liczebników: 1 minuta, 2 minuty, 5 minut. */
fun plural(n: Int, one: String, few: String, many: String): String {
    if (I18n.english) return if (n == 1) tr(one) else tr(many)
    val m10 = n % 10
    val m100 = n % 100
    return when {
        n == 1 -> one
        m10 in 2..4 && m100 !in 12..14 -> few
        else -> many
    }
}
