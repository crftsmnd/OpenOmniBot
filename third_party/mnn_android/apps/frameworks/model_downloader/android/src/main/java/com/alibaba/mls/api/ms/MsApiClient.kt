// Created by ruoyi.sjd on 2025/2/10.
// Copyright (c) 2024 Alibaba Group Holding Limited All rights reserved.
package com.alibaba.mls.api.ms

import com.google.gson.Gson
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

class MsApiClient {
    private val host = "modelscope.cn"
    private val gson = Gson()
    private var okHttpClient: OkHttpClient? = null

    init {
        createOkHttpClient()
    }

    private fun createOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor()
        logging.setLevel(HttpLoggingInterceptor.Level.BODY)
        val builder: OkHttpClient.Builder = OkHttpClient.Builder()
        builder.connectTimeout(30, TimeUnit.SECONDS)
        builder.addInterceptor(logging) //
        builder.readTimeout(30, TimeUnit.SECONDS)
        okHttpClient = builder.build()
        return okHttpClient!!
    }

    fun getModelFiles(modelGroup: String, modelPath: String): MsRepoInfo {
        val url = "https://$host/".toHttpUrl().newBuilder()
            .addPathSegment("api")
            .addPathSegment("v1")
            .addPathSegment("models")
            .addPathSegment(modelGroup)
            .addPathSegment(modelPath)
            .addPathSegment("repo")
            .addPathSegment("files")
            .addQueryParameter("Recursive", "1")
            .build()

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        okHttpClient!!.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("ModelScope repo request failed with code ${response.code}: ${response.message}")
            }
            val body = response.body?.string()
                ?: throw IllegalStateException("ModelScope repo response body is empty")
            return gson.fromJson(body, MsRepoInfo::class.java)
                ?: throw IllegalStateException("ModelScope repo response parse failed")
        }
    }
}
