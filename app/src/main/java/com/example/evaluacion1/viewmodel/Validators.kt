package com.example.evaluacion1.viewmodel

/** Validation problems a form field can have. The UI maps each one to a message. */
sealed interface FieldError {
    data object Required : FieldError
    data object InvalidEmail : FieldError
    data class TooShort(val min: Int) : FieldError
    data class TooLong(val max: Int) : FieldError
}

object Validators {

    private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    fun email(value: String): FieldError? = when {
        value.isBlank() -> FieldError.Required
        !EMAIL_REGEX.matches(value.trim()) -> FieldError.InvalidEmail
        else -> null
    }

    fun required(value: String): FieldError? =
        if (value.isBlank()) FieldError.Required else null

    fun length(value: String, min: Int, max: Int = Int.MAX_VALUE): FieldError? {
        val size = value.trim().length
        return when {
            size == 0 -> FieldError.Required
            size < min -> FieldError.TooShort(min)
            size > max -> FieldError.TooLong(max)
            else -> null
        }
    }
}
