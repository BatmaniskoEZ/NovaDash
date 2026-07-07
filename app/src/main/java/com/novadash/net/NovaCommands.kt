package com.novadash.net

/**
 * Novatek Wi-Fi API command IDs used by this app.
 *
 * Worked out by watching the stock app's Wi-Fi traffic and confirmed against a real camera.
 * Only the commands this app actually issues are listed here.
 *
 * Every command is issued as:  http://192.168.1.254/?custom=1&cmd=<id>[&par=..][&str=..]
 */
object NovaCommands {
    // --- Capture / recording ---
    // Command IDs below were confirmed by capturing the stock ARPHA app's own traffic
    // (RetrofitCreateHelper_TAG logcat) while toggling each setting on this exact camera.
    const val TAKE_PHOTO = 1001            // photo-mode capture (getCapture) — NOT supported on
                                           // this dashcam; wedges the HTTP server. Use MOVIE_SNAPSHOT.
    const val MOVIE_SNAPSHOT = 2017        // snapshot while recording (stock app getIMAGE, custom=1
                                           // &cmd=2017, no par) — the working capture on this camera
    const val MOVIE_RECORD = 2001          // par=1 start, par=0 stop
    const val MAX_RECORD_TIME = 2009       // remaining record time in seconds at current bitrate (<Value>)
    const val MOVIE_SIZE = 2002            // record resolution (par = index from 3030); wrap in LIVEVIEW 0/1
    const val MOVIE_LOOP = 2003            // loop interval INDEX (par: 0=1min, 1=3min, 2=5min)
    const val MOVIE_HDR = 2004             // WDR/HDR on/off (par=1/0); wrap in LIVEVIEW 0/1
    const val MOVIE_AUDIO = 2007           // record-audio on/off (par=1/0)
    const val LIVEVIEW = 2015              // liveview stream start/stop (par=1/0)
    const val GSENSOR_SENS = 2011          // G-sensor sensitivity (0=Off,1=High,2=Middle,3=Low)
    const val FREQUENCY = 6788             // light frequency (par: 0=50Hz, 1=60Hz)
    const val VOLUME = 6807                // prompt/beep volume (0=Off,1=Low,2=Medium,3=High)
    const val DATETIME_STAMP = 6809        // datetime watermark on/off (par=1/0)

    // --- Session / info ---
    const val LIST_COMMANDS = 3002         // dump supported commands
    const val GET_VERSION = 3012           // firmware version string
    const val GET_ALL_SETTINGS = 3014      // dump all current settings as Cmd/Status pairs
    const val FILE_LIST = 3015             // list SD-card files
    const val PING = 3016                  // heartbeat / liveness probe
    const val FREE_SPACE = 3017            // SD free space in bytes (<Value>)
    const val VIDEO_MODE_LIST = 3030       // available record resolutions

    // --- Files ---
    const val GET_THUMBNAIL = 4001         // str = FILE PATH (UPPER CASE), returns JPEG
    const val GET_PREVIEW = 4002           // str = FILE PATH (UPPER CASE)
    const val DELETE_FILE = 4003           // str = FILE PATH
    const val DELETE_ALL = 4004
    const val MOVIE_INFO = 4005            // str = FILE PATH

    // --- Date / time (str values, confirmed from stock app) ---
    const val SET_DATE = 3005              // str = YYYY-M-D
    const val SET_TIME = 3006              // str = HH:MM:SS

    // --- Wi-Fi / persistence ---
    const val GET_SSID_PW = 3029           // returns current SSID + password XML
    const val SET_SSID = 3003              // str = new SSID
    const val SET_PASSWORD = 3004          // str = new password
    const val SAVE_SETTINGS = 3021         // persist menu settings to PStore
    const val FORMAT_SD = 3010             // format SD card (destructive)

    /**
     * Undocumented commands still seen in the firmware dispatch table but not yet mapped to
     * a setting. Exposed only behind the experimental Settings panel; effects unverified.
     * (6807/6809/6788 were identified as volume/datetime/frequency and are now first-class.)
     */
    val EXPERIMENTAL = listOf(6802, 6811, 6812, 6820)
}
