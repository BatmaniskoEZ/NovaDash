package com.novadash.net.model

import org.simpleframework.xml.Element
import org.simpleframework.xml.ElementList
import org.simpleframework.xml.Root

/**
 * Response for cmd 3015 (file list), matching the real NA51055 schema:
 *
 * ```xml
 * <LIST>
 *   <ALLFile><File>
 *     <NAME>20260704193041_0000001F.MP4</NAME>
 *     <FPATH>A:\ARPHA\Normal\20260704193041_0000001F.MP4</FPATH>
 *     <SIZE>7662380</SIZE>
 *     <TIMECODE>1558485973</TIMECODE>
 *     <TIME>2026/07/04 19:30:42</TIME>
 *     <ATTR>32</ATTR>
 *   </File></ALLFile>
 *   <ALLFile><File> ... </File></ALLFile>   <!-- one <ALLFile> per file -->
 * </LIST>
 * ```
 *
 * Note the wrapper is repeated per file (each `<ALLFile>` holds exactly one `<File>`),
 * hence the inline list of [NovaAllFile] wrappers rather than a flat file list.
 */
@Root(name = "LIST", strict = false)
class NovaFileList {
    @field:ElementList(name = "ALLFile", inline = true, required = false)
    var wrappers: MutableList<NovaAllFile> = mutableListOf()

    val files: List<NovaFileEntry>
        get() = wrappers.mapNotNull { it.file }
}

@Root(name = "ALLFile", strict = false)
class NovaAllFile {
    @field:Element(name = "File", required = false)
    var file: NovaFileEntry? = null
}

@Root(name = "File", strict = false)
class NovaFileEntry {
    @field:Element(name = "NAME", required = false)
    var name: String? = null

    /** Camera-side path, e.g. `A:\ARPHA\Normal\FILE.MP4` (backslashes, `A:` drive). */
    @field:Element(name = "FPATH", required = false)
    var path: String? = null

    @field:Element(name = "SIZE", required = false)
    var size: Long = 0

    @field:Element(name = "TIMECODE", required = false)
    var timeCode: Long = 0

    /** Human timestamp, `yyyy/MM/dd HH:mm:ss`. */
    @field:Element(name = "TIME", required = false)
    var time: String? = null

    /** Attribute bitfield: bit0 set (odd, e.g. 33) => protected/event clip. */
    @field:Element(name = "ATTR", required = false)
    var attr: Int = 0

    val isVideo: Boolean get() = name?.endsWith(".MP4", ignoreCase = true) == true
    val isPhoto: Boolean get() = name?.endsWith(".JPG", ignoreCase = true) == true

    /** Event (G-sensor / locked) clips are stored under \Event and have an odd ATTR. */
    val isEvent: Boolean get() = attr and 1 == 1 || path?.contains("\\Event", true) == true

    /** Front vs rear camera, inferred from the trailing F/R before the extension. */
    val isRear: Boolean get() = name?.substringBeforeLast('.')?.endsWith("R", true) == true

    /**
     * HTTP URL to fetch the raw file: `A:\ARPHA\Normal\X.MP4` -> `<base>ARPHA/Normal/X.MP4`.
     * The camera serves SD files directly by path over HTTP.
     */
    fun downloadUrl(baseUrl: String): String? {
        val p = path ?: return null
        // Drop the "A:" drive prefix, normalise separators, strip the leading slash.
        val rel = p.substringAfter(':').replace('\\', '/').trimStart('/')
        return baseUrl.trimEnd('/') + "/" + rel
    }
}
