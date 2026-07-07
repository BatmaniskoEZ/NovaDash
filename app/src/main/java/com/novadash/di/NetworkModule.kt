package com.novadash.di

import com.novadash.net.NovaApi
import com.novadash.net.NovaClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.simpleframework.xml.convert.AnnotationStrategy
import org.simpleframework.xml.core.Persister
import retrofit2.Retrofit
import retrofit2.converter.simplexml.SimpleXmlConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun okHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            // The camera is on the local AP; keep timeouts short so a missing camera fails fast.
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            // BASIC only: BODY buffers the whole response into memory, which OOM-crashes on
            // large (100+ MB) file downloads despite @Streaming. Keep it light.
            .addInterceptor(
                HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
            )
            .build()

    @Provides
    @Singleton
    fun novaApi(client: OkHttpClient): NovaApi =
        Retrofit.Builder()
            .baseUrl(NovaClient.CAMERA_BASE_URL)
            .client(client)
            // AnnotationStrategy lets @Path/@ElementList work as annotated in the models.
            .addConverterFactory(
                SimpleXmlConverterFactory.createNonStrict(Persister(AnnotationStrategy()))
            )
            .build()
            .create(NovaApi::class.java)
}
