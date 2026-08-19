package io.legado.app.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * 豆包 TTS（火山引擎语音合成）客户端
 * 文档: https://www.volcengine.com/docs/6561/79823
 */
class DoubaoSpeakFetch {

    companion object {
        private const val TAG = "DoubaoSpeakFetch"
        private const val TTS_URL = "https://openspeech.bytedance.com/api/v1/tts"
        private const val PREFS_NAME = "TTS_CONFIG"
        private const val KEY_APP_ID = "doubao_app_id"
        private const val KEY_ACCESS_TOKEN = "doubao_access_token"
        private const val KEY_VOICE = "doubao_voice"
        const val DEFAULT_VOICE = "BV001_streaming"
        val VOICE_OPTIONS = listOf(
            "通用女声 - 灿灿@BV001_streaming",
            "通用男声 - 炀炀@BV002_streaming",
            "知性女声 - 擎苍@BV005_streaming",
            "温柔女声 - 栀栀@BV007_streaming",
            "活力男声 - 墨川@BV011_streaming",
            "甜美女声 - 小宁@BV012_streaming",
            "沉稳男声 - 叶凡@BV013_streaming",
            "亲切女声 - 晓辰@BV014_streaming",
            "青年男声 - 凌枫@BV015_streaming",
            "萝莉女声 - 豆包@BV021_streaming",
            "御姐女声 - 如月@BV024_streaming",
            "磁性男声 - 陈总@BV025_streaming",
            "阳光男声 - 子轩@BV026_streaming",
            "温柔男声 - 宇晨@BV027_streaming",
            "知性男声 - 承和@BV028_streaming",
            "元气女声 - 妙兮@BV032_streaming"
        )
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun getConfig(context: Context): Triple<String, String, String> {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val appId = sp.getString(KEY_APP_ID, "") ?: ""
        val accessToken = sp.getString(KEY_ACCESS_TOKEN, "") ?: ""
        val voice = sp.getString(KEY_VOICE, DEFAULT_VOICE) ?: DEFAULT_VOICE
        return Triple(appId, accessToken, voice)
    }

    fun saveConfig(context: Context, appId: String, accessToken: String, voice: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_APP_ID, appId)
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_VOICE, voice)
            .apply()
    }

    /**
     * 合成语音，返回音频 InputStream（mp3）
     */
    suspend fun synthesizeText(
        context: Context,
        speakText: String,
        rate: Int
    ): InputStream = withContext(Dispatchers.IO) {
        val (appId, accessToken, voice) = getConfig(context)
        if (appId.isBlank() || accessToken.isBlank()) {
            Log.e(TAG, "豆包TTS未配置AppId或AccessToken")
            return@withContext ByteArrayInputStream(ByteArray(0))
        }
        try {
            val speed = ((rate - 7) / 10f).coerceIn(0.2f, 3.0f)
            val reqId = UUID.randomUUID().toString()
            val json = JSONObject().apply {
                put("app", JSONObject().apply {
                    put("appid", appId)
                    put("token", accessToken)
                    put("cluster", "volcano_tts")
                })
                put("user", JSONObject().apply {
                    put("uid", "legado_tts")
                })
                put("audio", JSONObject().apply {
                    put("voice_type", voice)
                    put("encoding", "mp3")
                    put("speed", speed.toDouble())
                    put("volume", 1.0)
                    put("pitch", 1.0)
                })
                put("request", JSONObject().apply {
                    put("reqid", reqId)
                    put("text", speakText)
                    put("operation", "query")
                })
            }
            val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(TTS_URL)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "豆包TTS请求失败: ${response.code} ${response.body?.string()}")
                    return@withContext ByteArrayInputStream(ByteArray(0))
                }
                val respJson = JSONObject(response.body?.string() ?: "{}")
                if (respJson.optInt("code", -1) != 3000) {
                    Log.e(TAG, "豆包TTS返回错误: ${respJson.optString("message")}")
                    return@withContext ByteArrayInputStream(ByteArray(0))
                }
                val data = respJson.optJSONObject("data")
                val audioBase64 = data?.optString("audio") ?: ""
                if (audioBase64.isBlank()) {
                    Log.e(TAG, "豆包TTS返回音频为空")
                    return@withContext ByteArrayInputStream(ByteArray(0))
                }
                val audioBytes = android.util.Base64.decode(audioBase64, android.util.Base64.DEFAULT)
                Log.i(TAG, "豆包TTS合成成功: ${audioBytes.size} bytes, voice=$voice, speed=$speed")
                return@withContext ByteArrayInputStream(audioBytes)
            }
        } catch (e: Exception) {
            Log.e(TAG, "豆包TTS合成异常", e)
            ByteArrayInputStream(ByteArray(0))
        }
    }

    fun release() {
        // OkHttpClient 不需要特殊释放
    }
}
