package com.segment.analytics.edgefn.kotlin

import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URL


fun disableBundleURL(file: File) {
    val content = "// edge functions are disabled."
    FileOutputStream(file, false).use {
        it.write(content.toByteArray())
    }
}

fun download(from: String, file: File) {
    val url = URL(from)
    url.openStream().use { inp ->
        BufferedInputStream(inp).use { bis ->
            FileOutputStream(file, false).use { fos ->
                val len = 64 * 1024
                val data = ByteArray(len)
                var count: Int
                while (bis.read(data, 0, len).also { count = it } != -1) {
                    fos.write(data, 0, count)
                }
            }
        }
    }
}