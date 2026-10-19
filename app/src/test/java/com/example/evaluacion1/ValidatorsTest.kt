package com.example.evaluacion1

import com.example.evaluacion1.viewmodel.FieldError
import com.example.evaluacion1.viewmodel.Validators
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ValidatorsTest {

    @Test
    fun email_valid_returnsNull() {
        assertNull(Validators.email("solicitante@panalbit.test"))
    }

    @Test
    fun email_blank_isRequired() {
        assertEquals(FieldError.Required, Validators.email("   "))
    }

    @Test
    fun email_withoutDomain_isInvalid() {
        assertEquals(FieldError.InvalidEmail, Validators.email("solicitante@"))
    }

    @Test
    fun length_tooShort_returnsMinimum() {
        assertEquals(FieldError.TooShort(5), Validators.length("abc", min = 5))
    }

    @Test
    fun length_tooLong_returnsMaximum() {
        assertEquals(FieldError.TooLong(3), Validators.length("abcd", min = 1, max = 3))
    }

    @Test
    fun length_valid_returnsNull() {
        assertNull(Validators.length("abcdef", min = 5))
    }
}
