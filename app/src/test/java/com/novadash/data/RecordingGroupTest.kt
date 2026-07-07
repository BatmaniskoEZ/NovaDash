package com.novadash.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingGroupTest {

    private fun file(name: String, rear: Boolean) = MediaFile(
        name = name,
        cameraPath = "A:\\ARPHA\\Normal\\$name",
        size = 1,
        time = "2026/07/04 19:30:42",
        isVideo = true,
        isEvent = false,
        isRear = rear,
        downloadUrl = "http://x/$name",
        thumbnailUrl = "http://x/$name?thumb",
    )

    @Test
    fun pairsFrontWithNextSequenceRear() {
        val files = listOf(
            file("20260704193041_0000001F.MP4", rear = false),
            file("20260704193042_0000002R.MP4", rear = true),
            file("20260704203216_0000003F.MP4", rear = false),
            file("20260704203217_0000004R.MP4", rear = true),
        )
        val groups = RecordingGroup.pair(files)
        assertEquals(2, groups.size)
        assertTrue(groups[0].hasFront && groups[0].hasRear)
        assertEquals("20260704193041_0000001F.MP4", groups[0].front!!.name)
        assertEquals("20260704193042_0000002R.MP4", groups[0].rear!!.name)
    }

    @Test
    fun frontOnlyWhenNoMatchingRear() {
        val files = listOf(
            file("20260704193041_0000001F.MP4", rear = false),
            file("20260704193042_0000003F.MP4", rear = false),
        )
        val groups = RecordingGroup.pair(files)
        assertEquals(2, groups.size)
        assertTrue(groups.all { it.hasFront })
        assertFalse(groups.any { it.hasRear })
    }

    @Test
    fun orphanRearBecomesItsOwnGroup() {
        val files = listOf(file("20260704193042_0000002R.MP4", rear = true))
        val groups = RecordingGroup.pair(files)
        assertEquals(1, groups.size)
        assertNull(groups[0].front)
        assertEquals(groups[0].rear, groups[0].primary)
    }
}
