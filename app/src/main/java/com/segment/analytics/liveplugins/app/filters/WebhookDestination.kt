package com.segment.analytics.liveplugins.app.filters

import com.segment.analytics.kotlin.core.*
import com.segment.analytics.kotlin.core.platform.DestinationPlugin
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.platform.plugins.logger.*
import com.segment.analytics.kotlin.core.utilities.EncodeDefaultsJson
import kotlinx.serialization.encodeToString
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.util.concurrent.ExecutorService

// An After plugin that doesn't modify the incoming payload, and sends it to the desired webhook
class WebhookPlugin(
    private val webhookUrl: String,
    private val networkExecutor: ExecutorService
) : DestinationPlugin() {
    override val key: String = "Webhooks"
    override lateinit var analytics: Analytics

    fun log(message: String) {
        println("PRAY: $message")
    }

    /**
     * Sends a JSON payload to the specified webhookUrl, with the Content-Type=application/json
     * header set
     */
    private inline fun <reified T : BaseEvent?> sendPayloadToWebhook(payload: T?) =
        networkExecutor.submit {
            payload?.let {
                log(message = "Running ${payload.type} payload through $key")
                val requestedURL: URL = try {
                    URL(webhookUrl)
                } catch (e: MalformedURLException) {
                    throw IOException("Attempted to use malformed url: $webhookUrl", e)
                }

                val connection = requestedURL.openConnection() as HttpURLConnection
                connection.doOutput = true
                connection.setChunkedStreamingMode(0)
                connection.setRequestProperty("Content-Type", "application/json")

                val outputStream = DataOutputStream(connection.outputStream)
                val payloadJson = EncodeDefaultsJson.encodeToString(payload)
                outputStream.writeBytes(payloadJson)

                outputStream.use {
                    val responseCode = connection.responseCode
                    if (responseCode >= 300) {
                        var responseBody: String?
                        var inputStream: InputStream? = null
                        try {
                            inputStream = try {
                                connection.inputStream
                            } catch (ignored: IOException) {
                                connection.errorStream
                            }
                            responseBody =
                                inputStream?.bufferedReader()?.use(java.io.BufferedReader::readText)
                                    ?: ""
                        } catch (e: IOException) {
                            responseBody = (
                                    "Could not read response body for rejected message: " +
                                            e.toString()
                                    )
                        } finally {
                            inputStream?.close()
                        }
                        log(
                            message = "Failed to send payload, statusCode=$responseCode, body=$responseBody"
                        )
                    }
                }
            }
        }

    override fun track(payload: TrackEvent): BaseEvent? {
        sendPayloadToWebhook(payload)
        return payload
    }

    override fun identify(payload: IdentifyEvent): BaseEvent? {
        sendPayloadToWebhook(payload)
        return payload
    }

    override fun screen(payload: ScreenEvent): BaseEvent? {
        sendPayloadToWebhook(payload)
        return payload
    }

    override fun group(payload: GroupEvent): BaseEvent? {
        sendPayloadToWebhook(payload)
        return payload
    }

    override fun alias(payload: AliasEvent): BaseEvent? {
        sendPayloadToWebhook(payload)
        return payload
    }
}