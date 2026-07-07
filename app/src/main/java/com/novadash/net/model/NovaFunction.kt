package com.novadash.net.model

import org.simpleframework.xml.Element
import org.simpleframework.xml.Root

/**
 * Standard Novatek control-API response:
 *
 * ```xml
 * <Function>
 *   <Cmd>3016</Cmd>
 *   <Status>0</Status>
 *   <String>...</String>   <!-- present for value-returning commands e.g. 3012, 3029 -->
 * </Function>
 * ```
 *
 * `String`/`Value` are optional because most action commands only return a status.
 */
@Root(name = "Function", strict = false)
class NovaFunction {
    @field:Element(name = "Cmd", required = false)
    var cmd: Int = 0

    @field:Element(name = "Status", required = false)
    var status: Int = 0

    @field:Element(name = "String", required = false)
    var string: String? = null

    @field:Element(name = "Value", required = false)
    var value: String? = null

    /** Convenience: the payload most commands put in <String>, falling back to <Value>. */
    val payload: String?
        get() = string ?: value
}
