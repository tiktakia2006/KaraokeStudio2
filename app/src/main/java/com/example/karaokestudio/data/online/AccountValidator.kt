package com.example.karaokestudio.data.online

object AccountValidator {

    private val USERNAME = Regex("^[a-z0-9][a-z0-9_.-]{2,23}$")

    fun username(raw: String): String? {
        val value = raw.trim().lowercase()
        return when {
            value.length < 3 -> "Ім'я користувача має містити щонайменше 3 символи"
            value.length > 24 -> "Ім'я користувача не може бути довшим за 24 символи"
            !USERNAME.matches(value) -> "Дозволені латиниця, цифри, крапка, дефіс і підкреслення; починати треба з літери чи цифри"
            else -> null
        }
    }

    fun password(raw: String): String? = when {
        raw.length < 8 -> "Пароль має містити щонайменше 8 символів"
        raw.length > 128 -> "Пароль не може бути довшим за 128 символів"
        else -> null
    }

    fun nickname(raw: String): String? {
        val value = raw.trim()
        return when {
            value.length < 2 -> "Нікнейм має містити щонайменше 2 символи"
            value.length > 24 -> "Нікнейм не може бути довшим за 24 символи"
            else -> null
        }
    }
}
