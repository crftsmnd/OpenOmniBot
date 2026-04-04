// Created by ruoyi.sjd on 2024/12/25.
// Copyright (c) 2024 Alibaba Group Holding Limited All rights reserved.
package com.alibaba.mls.api

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Minimal HuggingFace API client for the framework
 * Only includes repo info fetching, no model search functionality
 */
class HfApiClient(val host: String) {
    private val gson = Gson()
    var okHttpClient: OkHttpClient? = null
        private set

    init {
        createOkHttpClient()
    }

    private fun createOkHttpClient(): OkHttpClient? {
        val builder: OkHttpClient.Builder = OkHttpClient.Builder()
        builder.connectTimeout(30, TimeUnit.SECONDS)
        builder.readTimeout(30, TimeUnit.SECONDS)
        okHttpClient = builder.build()
        return okHttpClient
    }

    // Get repo file tree
    fun getRepoTree(repoId: String, revision: String = "main"): List<HfTreeItem> {
        val baseUrl = "https://$host/".toHttpUrl()
        val urlBuilder = baseUrl.newBuilder()
            .addPathSegment("api")
            .addPathSegment("models")
        repoId.split("/").filter { it.isNotBlank() }.forEach { segment ->
            urlBuilder.addPathSegment(segment)
        }
        val url = urlBuilder
            .addPathSegment("tree")
            .addPathSegment(revision)
            .addQueryParameter("recursive", "true")
            .build()

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        okHttpClient!!.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HF repo tree request failed with code ${response.code}: ${response.message}")
            }
            val body = response.body?.string()
                ?: throw IllegalStateException("HF repo tree response body is empty")
            val type = object : TypeToken<List<HfTreeItem>>() {}.type
            return gson.fromJson(body, type)
                ?: throw IllegalStateException("HF repo tree response parse failed")
        }
    }

    companion object {
        const val HOST_DEFAULT = "huggingface.co"
        const val HOST_CN = "hf-mirror.com"

        val bestClient: HfApiClient?
            get() = HfApiClient(HOST_DEFAULT)  // Use huggingface.co directly
    }
}
