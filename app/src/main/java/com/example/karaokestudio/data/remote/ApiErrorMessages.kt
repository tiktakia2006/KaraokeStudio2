package com.example.karaokestudio.data.remote

object ApiErrorMessages {

    fun describe(error: Throwable): String = when (error) {
        is ApiException.NoNetwork -> "Немає з'єднання з сервером. Перевірте інтернет і адресу сервера."
        is ApiException.Unauthorized -> error.message ?: "Потрібно увійти в обліковий запис."
        is ApiException.RateLimited -> "Забагато запитів. Спробуйте через ${error.retryAfterSeconds.coerceAtLeast(1)} с."
        is ApiException.Server -> "Сервер тимчасово недоступний (${error.status}). Спробуйте пізніше."
        is ApiException.Malformed -> "Сервер надіслав незрозумілу відповідь."
        is ApiException.Client -> clientMessage(error)
        else -> error.message ?: "Невідома помилка"
    }

    private fun clientMessage(error: ApiException.Client): String = when (error.code) {
        "username_taken" -> "Таке ім'я користувача вже зайняте."
        "account_locked" -> "Забагато невдалих спроб входу. Обліковий запис тимчасово заблоковано."
        "upstream_error" -> "Зовнішній сервіс тимчасово недоступний. Спробуйте пізніше."
        "performance_not_synced" -> "Запис можна надіслати лише для виконання пісні з каталогу сервера, яке вже синхронізовано."
        "quota_exceeded" -> "На сервері закінчилося місце для ваших записів. Видаліть старі виконання."
        "payload_too_large" -> "Запис завеликий для сервера (максимум 10 МБ)."
        "unsupported_media_type" -> "Сервер не приймає такий формат запису."
        "no_file" -> "Файл запису не знайдено на пристрої."
        else -> error.message ?: "Запит відхилено (${error.status})"
    }
}
