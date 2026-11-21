package com.pharmabill.app

import com.pharmabill.app.util.BdPhone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BdPhoneTest {
    @Test fun acceptsAllOperators() {
        listOf("01312345678", "01412345678", "01512345678", "01612345678", "01712345678", "01812345678", "01912345678")
            .forEach { assertTrue(it, BdPhone.isValid(it)) }
    }

    @Test fun rejectsWrongNumbers() {
        assertFalse(BdPhone.isValid("0171234567"))     // 10 digits
        assertFalse(BdPhone.isValid("01212345678"))    // no operator 2
        assertFalse(BdPhone.isValid("02712345678"))    // landline style
        assertFalse(BdPhone.isValid("17123456789"))
        assertFalse(BdPhone.isValid(""))
    }

    @Test fun cleansPrefixesBanglaDigitsAndExtraDigits() {
        assertEquals("01712345678", BdPhone.clean("+880 1712-345678"))
        assertEquals("01712345678", BdPhone.clean("8801712345678"))
        assertEquals("01712345678", BdPhone.clean("008801712345678"))
        assertEquals("01712345678", BdPhone.clean("০১৭১২৩৪৫৬৭৮"))
        assertEquals("01712345678", BdPhone.clean("017123456789"))
        assertTrue(BdPhone.isValid("+8801712345678"))
    }

    @Test fun explainsProblem() {
        assertEquals(BdPhone.Problem.EMPTY, BdPhone.problem(""))
        assertEquals(BdPhone.Problem.NOT_01, BdPhone.problem("02"))
        assertEquals(BdPhone.Problem.BAD_OPERATOR, BdPhone.problem("012"))
        assertEquals(BdPhone.Problem.TOO_SHORT, BdPhone.problem("01712"))
        assertNull(BdPhone.problem("01712345678"))
    }
}
