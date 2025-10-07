package com.v2ray.ang.data.auth

import android.content.Context
import android.content.pm.PackageManager
import com.v2ray.ang.net.ApiClient
import com.v2ray.ang.net.StatusResponse

class AuthRepository(private val ctx: Context) {

    private fun appVersion(): String {
        return try {
            val pm = ctx.packageManager
            val pInfo = pm.getPackageInfo(ctx.packageName, 0)
            pInfo.versionName ?: "0.0.0"
        } catch (e: Exception) {
            "0.0.0"
        }
    }

    fun login(username: String, password: String, cb: (Result<String>) -> Unit) {
        val version = appVersion()
        ApiClient.postLogin(username, password, TokenStore.deviceId(ctx), version, cb)
    }

    fun status(token: String, cb: (Result<StatusResponse>) -> Unit) {
        ApiClient.getStatus(token, cb)
    }

    fun updatePromptSeen(token: String, cb: (Result<Unit>) -> Unit) {
        ApiClient.postUpdatePromptSeen(token, cb)
    }

    fun logout(token: String, cb: (Result<Unit>) -> Unit) {
        ApiClient.postLogout(token, cb)
    }

    fun reportAppUpdateIfNeeded(token: String, cb: (Result<Boolean>) -> Unit) {
        try {
            // نسخه فعلی اپ
            val pInfo = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            val current = pInfo.versionName ?: "0.0.0"

            // خواندن آخرین نسخهٔ گزارش‌شده از SharedPreferences
            val sp = ctx.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val lastReported = sp.getString("last_reported_app_version", null)

            // اگر تغییری نیست، نیازی به تماس با سرور نیست
            if (lastReported != null && lastReported == current) {
                cb(Result.success(false))
                return
            }

            // گزارش نسخهٔ جدید به پنل
            ApiClient.postReportUpdate(token, current) { r ->
                if (r.isSuccess) {
                    // ذخیرهٔ نسخهٔ گزارش‌شده
                    sp.edit().putString("last_reported_app_version", current).apply()
                    cb(Result.success(true))
                } else {
                    cb(Result.failure(r.exceptionOrNull() ?: Exception("نامشخص")))
                }
            }
        } catch (e: Exception) {
            cb(Result.failure(e))
        }
    }
}