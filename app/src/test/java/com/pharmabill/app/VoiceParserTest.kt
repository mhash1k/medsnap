package com.pharmabill.app

import com.pharmabill.app.ai.voice.BanglaTransliterator
import com.pharmabill.app.ai.voice.VoiceCommand
import com.pharmabill.app.ai.voice.VoiceParser
import com.pharmabill.app.domain.model.Flag
import com.pharmabill.app.domain.model.SaleUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceParserTest {
    private val p = VoiceParser(TestData.matcher(), { TestData.infos[it] })

    private fun add(text: String): VoiceCommand.AddItems {
        val c = p.parse(text).command
        assertTrue("expected add for '$text' but was $c", c is VoiceCommand.AddItems)
        return c as VoiceCommand.AddItems
    }

    @Test fun addTwoItems() {
        val items = add("Napa duita, Seclo ek pata").items
        assertEquals(2, items.size)
        assertEquals(1L, items[0].medicineId); assertEquals(SaleUnit.PIECE, items[0].unit); assertEquals(2, items[0].quantity)
        assertEquals(2L, items[1].medicineId); assertEquals(SaleUnit.STRIP, items[1].unit); assertEquals(1, items[1].quantity)
    }

    @Test fun splitOnAr() {
        val items = add("napa tinta ar fexo ek pata").items
        assertEquals(listOf(1L, 7L), items.map { it.medicineId })
        assertEquals(3, items[0].quantity)
    }

    @Test fun noNumberMeansOneDefaultUnit() {
        val it = add("fexo").items.single()
        assertEquals(SaleUnit.PIECE, it.unit); assertEquals(1, it.quantity)
    }

    @Test fun unitItemDefaultsToUnit() {
        val it = add("ceftron duita").items.single()
        assertEquals(8L, it.medicineId); assertEquals(SaleUnit.UNIT, it.unit); assertEquals(2, it.quantity)
    }

    @Test fun bottleHintPicksSyrup() {
        val it = add("napa ek botol").items.single()
        assertEquals(4L, it.medicineId); assertEquals(SaleUnit.UNIT, it.unit)
    }

    @Test fun boxAndHalfStrip() {
        assertEquals(SaleUnit.BOX, add("seclo ek box").items.single().unit)
        val half = add("napa adha pata").items.single()
        assertEquals(SaleUnit.PIECE, half.unit); assertEquals(6, half.quantity)
    }

    @Test fun strengthAfterName() {
        val it = add("seclo 40 duita").items.single()
        assertEquals(6L, it.medicineId); assertEquals(2, it.quantity)
    }

    @Test fun multiWordBrand() {
        assertEquals(5L, add("napa extra charta").items.single().medicineId)
    }

    @Test fun digitsAndEnglishNumbers() {
        assertEquals(10, add("Napa 10 ta").items.single().quantity)
        assertEquals(5, add("napa five").items.single().quantity)
        assertEquals(12, add("napa baro ta").items.single().quantity)
    }

    @Test fun removeItem() {
        val c = p.parse("Seclo bad dao").command
        assertTrue(c is VoiceCommand.RemoveItem)
        assertEquals(2L, (c as VoiceCommand.RemoveItem).item.medicineId)
    }

    @Test fun changeQuantity() {
        val c = p.parse("Napa tinta koro").command as VoiceCommand.ChangeQuantity
        assertEquals(1L, c.item.medicineId); assertEquals(3, c.item.quantity)
    }

    @Test fun stockQuery() {
        val c = p.parse("Napa stock koto?").command
        assertTrue(c is VoiceCommand.StockQuery)
        assertEquals(1L, (c as VoiceCommand.StockQuery).item.medicineId)
    }

    @Test fun priceQuery() {
        val c = p.parse("Ace er dam koto?").command
        assertTrue(c is VoiceCommand.PriceQuery)
        assertEquals(3L, (c as VoiceCommand.PriceQuery).item.medicineId)
    }

    @Test fun finishAndNewDraft() {
        assertEquals(VoiceCommand.FinishBill, p.parse("Bill koro").command)
        assertEquals(VoiceCommand.FinishBill, p.parse("confirm").command)
        assertEquals(VoiceCommand.NewDraft, p.parse("Notun bill").command)
    }

    @Test fun banglaScriptInput() {
        val items = add("নাপা দুইটা").items
        assertEquals(1L, items.single().medicineId)
        assertEquals(2, items.single().quantity)
        val two = add("নাপা দুইটা আর সেকলো এক পাতা").items
        assertEquals(listOf(1L, 2L), two.map { it.medicineId })
        assertEquals(SaleUnit.STRIP, two[1].unit)
    }

    @Test fun banglaDigitsAndQueries() {
        assertEquals(3, add("নাপা ৩টা").items.single().quantity)
        assertTrue(p.parse("নাপা স্টক কত").command is VoiceCommand.StockQuery)
        assertTrue(p.parse("এইস এর দাম কত").command is VoiceCommand.PriceQuery)
        assertEquals(VoiceCommand.FinishBill, p.parse("বিল করো").command)
        assertTrue(p.parse("সেকলো বাদ দাও").command is VoiceCommand.RemoveItem)
    }

    @Test fun transliteration() {
        assertEquals("napa", BanglaTransliterator.word("নাপা"))
        assertEquals("seklo", BanglaTransliterator.word("সেকলো"))
        assertEquals("123", BanglaTransliterator.convertDigits("১২৩"))
    }

    @Test fun lowConfidenceIsYellowNotDropped() {
        val it = add("nepa duita").items.single()
        assertEquals(1L, it.medicineId)
        assertEquals(Flag.YELLOW, it.flag)
    }

    @Test fun bestOfAlternativesWins() {
        val best = p.parseBest(listOf("blah blah", "napa duita", "nap a"))
        assertTrue(best.command is VoiceCommand.AddItems)
    }

    @Test fun nonsenseNotUnderstood() {
        assertTrue(p.parse("hello world").command is VoiceCommand.NotUnderstood)
    }
}
