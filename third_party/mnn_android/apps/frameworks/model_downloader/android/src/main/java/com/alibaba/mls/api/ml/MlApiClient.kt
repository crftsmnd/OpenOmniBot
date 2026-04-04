// Created by ruoyi.sjd on 2025/5/13.
// Copyright (c) 2024 Alibaba Group Holding Limited All rights reserved.

package com.alibaba.mls.api.ml

import androidx.annotation.Keep
import com.google.gson.Gson
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

class MlApiClient {
    private val host = "modelers.cn"
    private val gson = Gson()
    private var okHttpClient: OkHttpClient? = null

    init {
        createOkHttpClient()
    }

    private fun createOkHttpClient(): OkHttpClient? {
        val logging = HttpLoggingInterceptor()
        logging.setLevel(HttpLoggingInterceptor.Level.BODY)
        val builder: OkHttpClient.Builder = OkHttpClient.Builder()
        builder.connectTimeout(30, TimeUnit.SECONDS)
        builder.addInterceptor(logging)
        builder.readTimeout(30, TimeUnit.SECONDS)
        okHttpClient = builder.build()
        return okHttpClient
    }

    fun getModelFiles(
        modelGroup: String,
        modelPath: String,
        path: String,
    ): MlRepoInfo {
        val url = "https://$host/".toHttpUrl().newBuilder()
            .addPathSegment("api")
            .addPathSegment("v1")
            .addPathSegment("file")
            .addPathSegment(modelGroup)
            .addPathSegment(modelPath)
            .addQueryParameter("path", path)
            .build()

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        okHttpClient!!.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Modelers repo request failed with code ${response.code}: ${response.message}")
            }
            val body = response.body?.string()
                ?: throw IllegalStateException("Modelers repo response body is empty")
            return gson.fromJson(body, MlRepoInfo::class.java)
                ?: throw IllegalStateException("Modelers repo response parse failed")
        }
    }

    @Keep
    interface MlApiService {
        @GET("api/v1/file/{modelGroup}/{modelPath}")
        fun getModelFiles(
            @Path("modelGroup") modelGroup: String,
            @Path("modelPath") modelPath: String,
            @Query("path") path: String,
        ): retrofit2.Call<MlRepoInfo>
    }
}
