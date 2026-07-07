package com.novadash.net

import com.novadash.net.model.NovaFileList
import com.novadash.net.model.NovaFunction
import com.novadash.net.model.NovaVideoModeList
import com.novadash.net.model.NovaWifiInfo
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Streaming

/**
 * Retrofit binding for the Novatek control API at http://192.168.1.254.
 *
 * All commands share the `?custom=1&cmd=<id>` query shape. Callers should go through
 * [NovaClient] rather than hitting this directly, so requests are serialized (the camera's
 * HTTP server is single-threaded and drops concurrent requests).
 */
interface NovaApi {

    // IMPORTANT: `custom=1` MUST be the first query parameter. The camera's hfs server only
    // enters custom-command mode when it leads; otherwise it treats `/` as a file request and
    // returns plain text ("no A:\test.htm file"). Retrofit emits params in declaration order,
    // so `custom` stays first in every method below.

    /** Generic command returning the standard <Function> status envelope. */
    @GET("/")
    suspend fun command(
        @Query("custom") custom: Int = 1,
        @Query("cmd") cmd: Int,
        @Query("par") par: Int? = null,
        @Query("str") str: String? = null,
    ): NovaFunction

    /** cmd 3015 — file listing (separate `<LIST>` XML root). */
    @GET("/")
    suspend fun fileList(
        @Query("custom") custom: Int = 1,
        @Query("cmd") cmd: Int = NovaCommands.FILE_LIST,
    ): NovaFileList

    /** cmd 3029 — current SSID + passphrase (`<LIST>` root, not `<Function>`). */
    @GET("/")
    suspend fun wifiInfo(
        @Query("custom") custom: Int = 1,
        @Query("cmd") cmd: Int = NovaCommands.GET_SSID_PW,
    ): NovaWifiInfo

    /** cmd 3030 — available recording resolutions (`<Item>` list). */
    @GET("/")
    suspend fun videoModes(
        @Query("custom") custom: Int = 1,
        @Query("cmd") cmd: Int = NovaCommands.VIDEO_MODE_LIST,
    ): NovaVideoModeList

    /**
     * cmd 3014 — dump of every current menu setting as repeated `<Cmd>id</Cmd>
     * <Status>value</Status>` pairs. Returned raw so the repository can regex the pairs into
     * a map (Simple-XML can't model the repeated non-nested pairs).
     */
    @GET("/")
    suspend fun settingsDump(
        @Query("custom") custom: Int = 1,
        @Query("cmd") cmd: Int = NovaCommands.GET_ALL_SETTINGS,
    ): ResponseBody

    /**
     * Binary fetch used for thumbnails (4001) and file downloads. The camera serves file
     * bytes either via a cmd or by a direct GET of the (lower-cased) file path.
     */
    @Streaming
    @GET
    suspend fun download(@retrofit2.http.Url url: String): ResponseBody
}
