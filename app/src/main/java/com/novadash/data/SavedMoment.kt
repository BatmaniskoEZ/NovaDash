package com.novadash.data

/**
 * A moment the user flagged (typically from Android Auto while driving): the instant it
 * happened plus a short tag. Reconciled later with the dashcam's recorded clips.
 */
data class SavedMoment(
    val id: String,
    val epochMillis: Long,
    val tag: String = "",
    /** Where the moment was created, e.g. [SOURCE_ANDROID_AUTO] or [SOURCE_PHONE]. */
    val source: String = "",
) {
    companion object {
        const val SOURCE_ANDROID_AUTO = "androidauto"
        const val SOURCE_PHONE = "phone"
    }
}
