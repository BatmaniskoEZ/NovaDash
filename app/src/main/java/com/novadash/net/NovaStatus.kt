package com.novadash.net

/**
 * Status/error codes returned in the `<Status>` element of a Novatek response, plus the
 * asynchronous notification codes pushed over the :8192 socket.
 *
 * Positive codes are usually events
 * (record started, card inserted, ...); negative codes are failures; 0 is OK.
 */
object NovaStatus {
    const val OK = 0

    // Async events (also seen as notification codes on :8192)
    const val RECORD_STARTED = 1
    const val RECORD_STOPPED = 2
    const val DISCONNECT = 3
    const val MIC_ON = 4
    const val MIC_OFF = 5
    const val POWER_OFF = 6
    const val REMOVE_BY_USER = 7
    const val SENSOR_NUM_CHANGED = 8
    const val CARD_INSERT = 9
    const val CARD_REMOVE = 10

    // Failures
    const val NOFILE = -1
    const val FILE_LOCKED = -4
    const val FILE_ERROR = -5
    const val DELETE_FAILED = -6
    const val MOVIE_FULL = -7
    const val MOVIE_WR_ERROR = -8
    const val MOVIE_SLOW = -9
    const val BATTERY_LOW = -10
    const val STORAGE_FULL = -11
    const val FOLDER_FULL = -12
    const val FAIL = -13
    const val PAR_ERR = -21
    const val CMD_NOTFOUND = -256
    const val CMD_SOCKET_TIMEOUT = -1001
    const val CMD_CONNECT_TIMEOUT = -1002

    fun isSuccess(code: Int): Boolean = code >= 0

    /** Human-readable message for a status code; falls back to the raw number. */
    fun message(code: Int): String = when (code) {
        OK -> "OK"
        RECORD_STARTED -> "Recording started"
        RECORD_STOPPED -> "Recording stopped"
        DISCONNECT -> "Camera disconnected"
        MIC_ON -> "Microphone on"
        MIC_OFF -> "Microphone off"
        POWER_OFF -> "Camera powering off"
        CARD_INSERT -> "SD card inserted"
        CARD_REMOVE -> "SD card removed"
        NOFILE -> "File not found"
        FILE_LOCKED -> "File is locked"
        FILE_ERROR -> "File error"
        DELETE_FAILED -> "Delete failed"
        MOVIE_FULL, FOLDER_FULL, STORAGE_FULL -> "Storage full"
        MOVIE_WR_ERROR -> "Write error"
        MOVIE_SLOW -> "SD card too slow"
        BATTERY_LOW -> "Battery low"
        PAR_ERR -> "Invalid parameter"
        CMD_NOTFOUND -> "Command not supported"
        CMD_SOCKET_TIMEOUT, CMD_CONNECT_TIMEOUT -> "Camera timed out"
        else -> "Camera error ($code)"
    }
}
