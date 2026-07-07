package com.novadash.net.model

import org.simpleframework.xml.Element
import org.simpleframework.xml.Root

/**
 * Response for cmd 3029 (current Wi-Fi credentials). Uses a bare `<LIST>` root rather than
 * the `<Function>` envelope:
 *
 * ```xml
 * <LIST>
 *   <SSID>D25-</SSID>
 *   <PASSPHRASE>12345678</PASSPHRASE>
 * </LIST>
 * ```
 */
@Root(name = "LIST", strict = false)
class NovaWifiInfo {
    @field:Element(name = "SSID", required = false)
    var ssid: String? = null

    @field:Element(name = "PASSPHRASE", required = false)
    var passphrase: String? = null
}
