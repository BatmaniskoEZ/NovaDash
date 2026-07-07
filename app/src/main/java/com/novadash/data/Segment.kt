package com.novadash.data

/**
 * A cut segment on a clip, in seconds. [end] is null while only the in-point is set (the user
 * is mid-marking). Maps directly to a LosslessCut `cutSegments` entry.
 */
data class Segment(
    val start: Double,
    val end: Double? = null,
    val name: String = "",
) {
    val isComplete: Boolean get() = end != null && end > start
}
