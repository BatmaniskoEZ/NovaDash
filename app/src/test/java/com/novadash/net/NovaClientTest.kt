package com.novadash.net

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.simpleframework.xml.convert.AnnotationStrategy
import org.simpleframework.xml.core.Persister
import retrofit2.Retrofit
import retrofit2.converter.simplexml.SimpleXmlConverterFactory

/**
 * Exercises the M1 command round-trip against a mock HTTP server that mimics the Novatek
 * `<Function>` envelope. Confirms status parsing, payload extraction, and error mapping
 * without a physical camera.
 */
class NovaClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: NovaClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(
                SimpleXmlConverterFactory.createNonStrict(Persister(AnnotationStrategy()))
            )
            .build()
            .create(NovaApi::class.java)
        client = NovaClient(api)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun ping_ok_returnsTrue() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """<?xml version="1.0"?><Function><Cmd>3016</Cmd><Status>0</Status></Function>"""
            )
        )
        assertTrue(client.ping())
    }

    @Test
    fun version_extractsPayload() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """<?xml version="1.0"?><Function><Cmd>3012</Cmd><Status>0</Status><String>NA51055_V1.2.3</String></Function>"""
            )
        )
        val result = client.command(NovaCommands.GET_VERSION)
        assertTrue(result is NovaResult.Ok)
        assertEquals("NA51055_V1.2.3", (result as NovaResult.Ok).value.payload)
    }

    @Test
    fun negativeStatus_mapsToError() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """<?xml version="1.0"?><Function><Cmd>2001</Cmd><Status>-11</Status></Function>"""
            )
        )
        val result = client.command(NovaCommands.MOVIE_RECORD, par = 1)
        assertTrue(result is NovaResult.Err)
        assertEquals(NovaStatus.STORAGE_FULL, (result as NovaResult.Err).status)
        assertEquals("Storage full", result.message)
    }

    @Test
    fun unreachableCamera_pingFalse() = runTest {
        server.shutdown() // simulate no camera
        assertFalse(client.ping())
    }
}
