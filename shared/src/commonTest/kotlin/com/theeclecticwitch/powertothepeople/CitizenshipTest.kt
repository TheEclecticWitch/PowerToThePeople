package com.theeclecticwitch.powertothepeople

import com.theeclecticwitch.powertothepeople.officials.Birthplace
import com.theeclecticwitch.powertothepeople.officials.birthplaceText
import com.theeclecticwitch.powertothepeople.officials.bornAbroad
import com.theeclecticwitch.powertothepeople.officials.citizenshipRequirement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CitizenshipTest {
    @Test
    fun birthplacesReadNaturally() {
        assertEquals("Suitland, Maryland", birthplaceText(Birthplace("Suitland", "Maryland", "United States")))
        assertEquals("Karachi, Pakistan", birthplaceText(Birthplace("Karachi", null, "Pakistan")))
        // A U.S. place Wikidata gives no state for, and a place that is its own state.
        assertEquals("Washington, D.C., United States", birthplaceText(Birthplace("Washington, D.C.", null, "United States")))
        assertEquals("Hawaii", birthplaceText(Birthplace("Hawaii", "Hawaii", "United States")))
        assertNull(birthplaceText(Birthplace(null, "Ohio", "United States")))
        assertTrue(bornAbroad(Birthplace("Heidelberg", null, "Germany")))
    }

    @Test
    fun eachOfficeCitesItsOwnRequirement() {
        assertTrue(citizenshipRequirement("U.S. Senator for Maryland")!!.contains("9 years"))
        assertTrue(citizenshipRequirement("U.S. Representative, Maryland District 5")!!.contains("7 years"))
        assertTrue(citizenshipRequirement("President of the United States")!!.contains("natural-born"))
        assertTrue(citizenshipRequirement("Vice President of the United States")!!.contains("12th Amendment"))
        assertNull(citizenshipRequirement("Delegate for the District of Columbia"))
    }
}
