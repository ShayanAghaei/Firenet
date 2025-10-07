package com.v2ray.ang.net

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

private const val BASE = "https://report.soft99.sbs:2053"

data class StatusResponse(
    val username: String? = null,
    val used_traffic: Long? = null,
    val data_limit: Long? = null,
    val expire: Long? = null,
    val status: String? = null,
    val links: List<String>? = null,
    val need_to_update: Boolean? = null,
    val is_ignoreable: Boolean? = null
)


object ApiClient {

    private fun readBody(conn: HttpURLConnection): String {
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        return BufferedReader(InputStreamReader(stream)).use { it.readText() }
    }

    fun postLogin(
        username: String,
        password: String,
        deviceId: String,
        appVersion: String,
        cb: (Result<String>) -> Unit
    ) {
        Thread {
            try {
                val url = URL("$BASE/api/login")
                val conn = (url.openConnection() as HttpsURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connectTimeout = 15000
                    readTimeout = 20000
                    doOutput = true
                }
                val body = JSONObject()
                    .put("username", username)
                    .put("password", password)
                    .put("device_id", deviceId)
                    .put("app_version", appVersion)
                    .toString()

                conn.outputStream.use { os: OutputStream ->
                    os.write(body.toByteArray(Charsets.UTF_8))
                }

                val text = readBody(conn)
                if (conn.responseCode in 200..299) {
                    val token = JSONObject(text).optString("token", "")
                    if (token.isNullOrEmpty()) cb(Result.failure(Exception("توکن دریافت نشد")))
                    else cb(Result.success(token))
                } else {
                    val j = runCatching { JSONObject(text) }.getOrNull()
                    val msg = j?.optString("message")?.ifEmpty { j?.optString("error") }
                        ?: "خطای ${conn.responseCode}"
                    cb(Result.failure(Exception(msg)))
                }
            } catch (e: Exception) {
                cb(Result.failure(e))
            }
        }.start()
    }

    fun getStatus(
        token: String,
        cb: (Result<StatusResponse>) -> Unit
    ) {
        Thread {
            try {
                val url = URL("$BASE/api/status")
                val conn = (url.openConnection() as HttpsURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Authorization", "Bearer $token")
                    setRequestProperty("Accept", "application/json")
                    connectTimeout = 15000
                    readTimeout = 20000
                    doInput = true
                }

                val text = readBody(conn)
                if (conn.responseCode in 200..299) {
                    val j = JSONObject(text)

                    val linksArr: JSONArray? = j.optJSONArray("links")
                    val links: List<String>? = linksArr?.let { arr ->
                        List(arr.length()) { idx -> arr.optString(idx) }
                    }

                    val needUpdate: Boolean? = when {
                        j.has("need_to_update") -> if (j.isNull("need_to_update")) null else j.optBoolean("need_to_update")
                        else -> null
                    }
                    val isIgnoreable: Boolean? = when {
                        j.has("is_ignoreable") -> if (j.isNull("is_ignoreable")) null else j.optBoolean("is_ignoreable")
                        else -> null
                    }

                    val resp = StatusResponse(
                        username = j.optString("username", null),
                        used_traffic = if (j.isNull("used_traffic")) null else j.optLong("used_traffic"),
                        data_limit = if (j.isNull("data_limit")) null else j.optLong("data_limit"),
                        expire = if (j.isNull("expire")) null else j.optLong("expire"),
                        status = j.optString("status", null),
                        links = links,
                        need_to_update = needUpdate,
                        is_ignoreable = isIgnoreable
                    )
                    cb(Result.success(resp))
                } else {
                    val j = runCatching { JSONObject(text) }.getOrNull()
                    val msg = j?.optString("message")?.ifEmpty { j?.optString("error") }
                        ?: "خطای ${conn.responseCode}"
                    cb(Result.failure(Exception(msg)))
                }
            } catch (e: Exception) {
                cb(Result.failure(e))
            }
        }.start()
    }

    fun postKeepAlive(
        token: String,
        cb: (Result<Unit>) -> Unit
    ) {
        Thread {
            try {
                val url = URL("$BASE/api/keepalive")
                val conn = (url.openConnection() as HttpsURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Authorization", "Bearer $token")
                    connectTimeout = 15000
                    readTimeout = 20000
                    doOutput = true
                }
                conn.outputStream.use { it.write("{}".toByteArray(Charsets.UTF_8)) }
                readBody(conn) // برای مصرف استریم

                when (conn.responseCode) {
                    in 200..299 -> cb(Result.success(Unit))
                    401 -> cb(Result.failure(Exception("401")))
                    else -> {
                        val msg = runCatching { org.json.JSONObject(readBody(conn)).optString("message") }.getOrNull()
                        cb(Result.failure(Exception(msg ?: "خطای ${conn.responseCode}")))
                    }
                }
            } catch (e: Exception) {
                cb(Result.failure(e))
            }
        }.start()
    }
    
    // + ADD
    fun postUpdateFcmToken(
        token: String,
        fcmToken: String,
        cb: (Result<Unit>) -> Unit
    ) {
        Thread {
            try {
                val url = URL("$BASE/api/update-fcm-token")
                val conn = (url.openConnection() as HttpsURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Authorization", "Bearer $token")
                    connectTimeout = 15000
                    readTimeout = 20000
                    doOutput = true
                }
                val body = org.json.JSONObject().put("fcm_token", fcmToken).toString()
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

                val text = readBody(conn)
                if (conn.responseCode in 200..299) cb(Result.success(Unit)) else {
                    val j = runCatching { org.json.JSONObject(text) }.getOrNull()
                    val msg = j?.optString("message")?.ifEmpty { j?.optString("error") }
                        ?: "خطای ${conn.responseCode}"
                    cb(Result.failure(Exception(msg)))
                }
            } catch (e: Exception) {
                cb(Result.failure(e))
            }
        }.start()
    }


    fun postLogout(
        token: String,
        cb: (Result<Unit>) -> Unit
    ) {
        Thread {
            try {
                val url = URL("$BASE/api/logout")
                val conn = (url.openConnection() as HttpsURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Authorization", "Bearer $token")
                    setRequestProperty("Accept", "application/json")
                    connectTimeout = 15000
                    readTimeout = 20000
                    doInput = true
                    // لاگ‌اوت طبق مستندات بدنه ندارد؛ doOutput ست نمی‌شود و چیزی نمی‌نویسیم.
                }

                val text = readBody(conn) // برای استخراج پیام سرور در صورت نیاز
                if (conn.responseCode in 200..299) {
                    cb(Result.success(Unit))
                } else {
                    val j = runCatching { JSONObject(text) }.getOrNull()
                    val msg = j?.optString("message")?.ifEmpty { j?.optString("error") }
                        ?: "خطای ${conn.responseCode}"
                    cb(Result.failure(Exception(msg)))
                }
            } catch (e: Exception) {
                cb(Result.failure(e))
            }
        }.start()
    }

    fun postUpdatePromptSeen(
        token: String,
        cb: (Result<Unit>) -> Unit
    ) {
        Thread {
            try {
                val url = URL("$BASE/api/update-prompt-seen")
                val conn = (url.openConnection() as HttpsURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Authorization", "Bearer $token")
                    connectTimeout = 15000
                    readTimeout = 20000
                    doOutput = true
                }
                conn.outputStream.use { it.write("{}".toByteArray(Charsets.UTF_8)) }

                val text = readBody(conn)
                if (conn.responseCode in 200..299) cb(Result.success(Unit)) else {
                    val j = runCatching { JSONObject(text) }.getOrNull()
                    val msg = j?.optString("message")?.ifEmpty { j?.optString("error") }
                        ?: "خطای ${conn.responseCode}"
                    cb(Result.failure(Exception(msg)))
                }
            } catch (e: Exception) {
                cb(Result.failure(e))
            }
        }.start()
    }

    fun postReportUpdate(
        token: String,
        newVersion: String,
        cb: (Result<Unit>) -> Unit
    ) {
        Thread {
            try {
                val url = URL("$BASE/api/report-update")
                val conn = (url.openConnection() as HttpsURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Authorization", "Bearer $token")
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                    connectTimeout = 15000
                    readTimeout = 20000
                    doOutput = true
                }

                val body = JSONObject().put("new_version", newVersion).toString()
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

                val text = readBody(conn)
                if (conn.responseCode in 200..299) {
                    cb(Result.success(Unit))
                } else {
                    val j = runCatching { JSONObject(text) }.getOrNull()
                    val msg = j?.optString("message")?.ifEmpty { j?.optString("error") }
                        ?: "خطای ${conn.responseCode}"
                    cb(Result.failure(Exception(msg)))
                }
            } catch (e: Exception) {
                cb(Result.failure(e))
            }
        }.start()
    }
}
