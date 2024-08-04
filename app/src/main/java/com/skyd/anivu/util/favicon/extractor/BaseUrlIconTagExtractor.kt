package com.skyd.anivu.util.favicon.extractor

import kotlinx.coroutines.runBlocking
import retrofit2.Retrofit
import javax.inject.Inject

class BaseUrlIconTagExtractor @Inject constructor(
    retrofit: Retrofit,
) : IconTagExtractor(retrofit) {
    override fun intercept(url: String): List<Extractor.IconData> = runBlocking {
        baseUrl(url)
            ?.let { base -> super.intercept(base) }
            .orEmpty()
    }
}