package com.novadash.net

import com.novadash.net.model.NovaFileList
import com.novadash.net.model.NovaWifiInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.simpleframework.xml.convert.AnnotationStrategy
import org.simpleframework.xml.core.Persister

/**
 * Parses the exact XML captured from the real NA51055 camera (cmd 3015 / 3029) to lock the
 * response schema and the derived helpers (event vs normal, front vs rear, download URL).
 */
class NovaFileListTest {
    private val persister = Persister(AnnotationStrategy())

    private val fileListXml = """
        <?xml version="1.0" encoding="UTF-8" ?>
        <LIST>
        <ALLFile><File>
        <NAME>20260704193041_0000001F.MP4</NAME>
        <FPATH>A:\ARPHA\Normal\20260704193041_0000001F.MP4</FPATH>
        <SIZE>7662380</SIZE>
        <TIMECODE>1558485973</TIMECODE>
        <TIME>2026/07/04 19:30:42</TIME>
        <ATTR>32</ATTR></File>
        </ALLFile>
        <ALLFile><File>
        <NAME>20260704203216_0000003F.MP4</NAME>
        <FPATH>A:\ARPHA\Event\20260704203216_0000003F.MP4</FPATH>
        <SIZE>186391516</SIZE>
        <TIMECODE>1558488104</TIMECODE>
        <TIME>2026/07/04 20:33:16</TIME>
        <ATTR>33</ATTR></File>
        </ALLFile>
        <ALLFile><File>
        <NAME>20260704203217_0000004R.MP4</NAME>
        <FPATH>A:\ARPHA\Event\20260704203217_0000004R.MP4</FPATH>
        <SIZE>75783584</SIZE>
        <TIMECODE>1558488104</TIMECODE>
        <TIME>2026/07/04 20:33:16</TIME>
        <ATTR>33</ATTR></File>
        </ALLFile>
        </LIST>
    """.trimIndent()

    @Test
    fun parsesAllThreeFiles() {
        val list = persister.read(NovaFileList::class.java, fileListXml)
        assertEquals(3, list.files.size)
        assertEquals("20260704193041_0000001F.MP4", list.files[0].name)
        assertEquals(7662380L, list.files[0].size)
    }

    @Test
    fun classifiesEventAndLens() {
        val list = persister.read(NovaFileList::class.java, fileListXml)
        val normalFront = list.files[0]
        val eventRear = list.files[2]

        assertFalse(normalFront.isEvent)
        assertFalse(normalFront.isRear)
        assertTrue(normalFront.isVideo)

        assertTrue(eventRear.isEvent)   // ATTR 33 + \Event path
        assertTrue(eventRear.isRear)    // trailing R
    }

    @Test
    fun buildsHttpDownloadUrl() {
        val list = persister.read(NovaFileList::class.java, fileListXml)
        assertEquals(
            "http://192.168.1.254/ARPHA/Normal/20260704193041_0000001F.MP4",
            list.files[0].downloadUrl("http://192.168.1.254/"),
        )
    }

    @Test
    fun parsesWifiInfo() {
        val xml = """<?xml version="1.0" encoding="UTF-8" ?>
            <LIST><SSID>D25-</SSID><PASSPHRASE>12345678</PASSPHRASE></LIST>""".trimIndent()
        val info = persister.read(NovaWifiInfo::class.java, xml)
        assertEquals("D25-", info.ssid)
        assertEquals("12345678", info.passphrase)
    }
}
