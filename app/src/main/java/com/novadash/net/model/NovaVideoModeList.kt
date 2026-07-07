package com.novadash.net.model

import org.simpleframework.xml.Element
import org.simpleframework.xml.ElementList
import org.simpleframework.xml.Root

/**
 * Response for cmd 3030 (video mode / recording-resolution list):
 *
 * ```xml
 * <LIST>
 *   <Item><Name>4K 3840*2160P</Name><Index>6</Index><Size>4K 3840*2160P</Size>...</Item>
 *   ...
 * </LIST>
 * ```
 * The `Index` is the value passed to cmd 2002 (MOVIE_SIZE) to select that resolution.
 */
@Root(name = "LIST", strict = false)
class NovaVideoModeList {
    @field:ElementList(name = "Item", inline = true, required = false)
    var items: MutableList<NovaVideoMode> = mutableListOf()
}

@Root(name = "Item", strict = false)
class NovaVideoMode {
    @field:Element(name = "Name", required = false)
    var name: String? = null

    @field:Element(name = "Index", required = false)
    var index: Int = 0

    @field:Element(name = "Size", required = false)
    var size: String? = null
}
