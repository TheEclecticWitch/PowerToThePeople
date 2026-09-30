package com.theeclecticwitch.powertothepeople

import com.theeclecticwitch.powertothepeople.data.FileStore
import com.theeclecticwitch.powertothepeople.location.congressOn
import com.theeclecticwitch.powertothepeople.ui.Format
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.LocalDate
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

class BasicsTest {
    @Test
    fun congressNumberChangesAtNoonJanuaryThird() {
        assertEquals(119, congressOn(LocalDate(2026, 9, 29)))
        assertEquals(119, congressOn(LocalDate(2027, 1, 2)))
        assertEquals(120, congressOn(LocalDate(2027, 1, 3)))
        assertEquals(1, congressOn(LocalDate(1789, 3, 4)))
    }

    @Test
    fun moneyReadsTheWayPeopleSayIt() {
        assertEquals("$40,102,185,696,865", Format.dollars(40102185696865.37))
        assertEquals("$1.97 trillion", Format.dollarsShort(1965591017473.53))
        assertEquals("-$215.0 billion", Format.dollarsShort(-215024135197.77))
        assertEquals("$999", Format.dollars(999.4))
        assertEquals("0.05", Format.decimals(0.049, 2))
    }

    @Test
    fun datesAndOrdinals() {
        assertEquals("September 28, 2026", Format.date("2026-09-28"))
        assertEquals("119th", Format.ordinal(119))
        assertEquals("121st", Format.ordinal(121))
        assertEquals("112th", Format.ordinal(112))
        assertEquals("not a date", Format.date("not a date"))
    }

    @Test
    fun fileStoreWritesWholeFilesOrNothing() {
        val fs = FakeFileSystem()
        val store = FileStore("/app".toPath(), fs)
        assertNull(store.read("x.json"))
        store.write("x.json", "one")
        store.write("x.json", "two")
        assertEquals("two", store.read("x.json"))
        // No temporary file left beside it.
        assertEquals(listOf("/app/x.json".toPath()), fs.list("/app".toPath()))
        store.delete("x.json")
        assertNull(store.read("x.json"))
    }
}
