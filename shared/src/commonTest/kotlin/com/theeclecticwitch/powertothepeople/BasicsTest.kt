package com.theeclecticwitch.powertothepeople

import com.theeclecticwitch.powertothepeople.data.FileStore
import com.theeclecticwitch.powertothepeople.doomsday.Doomsday
import com.theeclecticwitch.powertothepeople.location.communityName
import com.theeclecticwitch.powertothepeople.location.congressOn
import com.theeclecticwitch.powertothepeople.officials.districtMatches
import com.theeclecticwitch.powertothepeople.voting.nextFederalElection
import com.theeclecticwitch.powertothepeople.voting.voteGovRegister
import com.theeclecticwitch.powertothepeople.ui.Format
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
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
    fun marylandElectionDistrictsReadAsTheirCommunity() {
        assertEquals("Waldorf", communityName("District 6, Waldorf"))
        assertEquals("Springfield township", communityName("Springfield township"))
        assertEquals("District Heights", communityName("District Heights"))
    }

    @Test
    fun doomsdayClockReadsInWordsAndOnAClock() {
        assertEquals("85 seconds to midnight", Doomsday.words(85))
        assertEquals("2 minutes to midnight", Doomsday.words(120))
        assertEquals("61 seconds, or 1 minute and 1 second to midnight", Doomsday.spelledOut(61))
        assertEquals("1 minute to midnight", Doomsday.words(60))
        assertEquals("85 seconds, or 1 minute and 25 seconds to midnight", Doomsday.spelledOut(85))
        assertEquals("11:58:35", Doomsday.clockTime(85))
        assertEquals("11:43:00", Doomsday.clockTime(17 * 60))
    }

    @Test
    fun censusDistrictNamesMatchTheStatesOwn() {
        assertTrue(districtMatches("State Senate District 28", "28"))
        assertTrue(districtMatches("State Legislative District 28", "28"))
        assertTrue(districtMatches("State Legislative Subdistrict 29A", "29A"))
        assertTrue(districtMatches("State House District 7", "07"))
        assertTrue(districtMatches("10th Bristol District", "10th Bristol"))
        assertFalse(districtMatches("State Senate District 28", "2"))
        assertFalse(districtMatches("State Legislative Subdistrict 29A", "29B"))
        assertFalse(districtMatches(null, "28"))
    }

    @Test
    fun surnamesKeepTheirParticlesAndDropSuffixes() {
        assertEquals("Van Hollen", Format.surname("Chris Van Hollen"))
        assertEquals("Hoyer", Format.surname("Steny H. Hoyer"))
        assertEquals("Suozzi", Format.surname("Thomas R. Suozzi Jr."))
        assertEquals("De La Cruz", Format.surname("Monica De La Cruz"))
        assertEquals("Trump", Format.surname("Donald Trump"))
        assertEquals("Moore", Format.surname("Moore"))
    }

    @Test
    fun electionDayIsTheTuesdayAfterTheFirstMondayInNovember() {
        assertEquals(LocalDate(2026, 11, 3), nextFederalElection(LocalDate(2026, 10, 3)))
        assertEquals(LocalDate(2026, 11, 3), nextFederalElection(LocalDate(2026, 11, 3)))
        assertEquals(LocalDate(2028, 11, 7), nextFederalElection(LocalDate(2026, 11, 4)))
        assertEquals(LocalDate(2026, 11, 3), nextFederalElection(LocalDate(2025, 1, 1)))
        // November 1, 2022 was a Tuesday, but the election was the 8th: it must follow a Monday in November.
        assertEquals(LocalDate(2022, 11, 8), nextFederalElection(LocalDate(2022, 10, 1)))
        assertEquals("https://vote.gov/register/new-hampshire", voteGovRegister("NH"))
        assertEquals("https://vote.gov/register/district-of-columbia", voteGovRegister("DC"))
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
