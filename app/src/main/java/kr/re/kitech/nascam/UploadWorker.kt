package kr.re.kitech.nascam

import android.content.Context
import android.util.Log
import androidx.work.*
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import android.webkit.MimeTypeMap
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/** 촬영된 사진을 Synology WebDAV로 업로드. 네트워크 실패 시 WorkManager가 자동 재시도. */
class UploadWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val path = inputData.getString(KEY_PATH) ?: return Result.failure()
        val mime = inputData.getString(KEY_MIME) ?: guessMime(path)
        val file = File(path)
        if (!file.exists()) return Result.failure()

        val prefs = Prefs(applicationContext)
        if (!prefs.isConfigured) return Result.retry()

        val auth = Credentials.basic(prefs.user, prefs.pass, Charsets.UTF_8)
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()

        var dir = "${prefs.url}/${prefs.folder}"
        if (prefs.dateFolder) {
            dir += "/" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(file.lastModified()))
        }

        return try {
            ensureDirs(client, auth, prefs.url, dir.removePrefix(prefs.url).trim('/'))

            val req = Request.Builder()
                .url("$dir/${file.name}")
                .header("Authorization", auth)
                .put(file.asRequestBody(mime.toMediaTypeOrNull()))
                .build()
            client.newCall(req).execute().use { res ->
                Log.i(TAG, "PUT ${file.name} -> ${res.code}")
                when {
                    res.isSuccessful -> {
                        prefs.lastError = ""
                        if (prefs.deleteAfter) file.delete()
                        Result.success()
                    }
                    res.code == 401 || res.code == 403 -> {
                        prefs.lastError = "인증 실패(${res.code}): ID/비밀번호 또는 폴더 권한 확인"
                        Result.failure()
                    }
                    else -> {
                        prefs.lastError = "서버 응답 ${res.code} (${res.message})"
                        if (runAttemptCount < 10) Result.retry() else Result.failure()
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "upload failed: ${e.message}")
            prefs.lastError = "${e.javaClass.simpleName}: ${e.message}"
            if (runAttemptCount < 10) Result.retry() else Result.failure()
        }
    }

    /** 경로의 각 단계에 MKCOL (이미 있으면 405 → 무시) */
    private fun ensureDirs(client: OkHttpClient, auth: String, base: String, rel: String) {
        var cur = base
        for (seg in rel.split('/').filter { it.isNotBlank() }) {
            cur += "/$seg"
            val req = Request.Builder().url(cur).header("Authorization", auth)
                .method("MKCOL", null).build()
            client.newCall(req).execute().use { r ->
                if (r.code == 401 || r.code == 403) throw IllegalStateException("인증 실패 ${r.code} (MKCOL $cur)")
            }
        }
    }

    companion object {
        const val TAG = "NasCam"

        /** 설정 화면 연결 테스트: 폴더에 OPTIONS 요청 */
        fun testConnection(prefs: Prefs): String = try {
            val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).build()
            val req = Request.Builder().url("${prefs.url}/${prefs.folder}")
                .header("Authorization", Credentials.basic(prefs.user, prefs.pass, Charsets.UTF_8))
                .method("OPTIONS", null).build()
            client.newCall(req).execute().use { r ->
                val dav = r.header("DAV")
                when {
                    r.code == 401 || r.code == 403 -> "인증 실패(${r.code}): ID/비밀번호 확인"
                    r.code == 404 -> "폴더 없음(404): 공유폴더 이름 확인 (앱이 하위폴더는 자동 생성)"
                    r.isSuccessful && dav != null -> "연결 성공 (WebDAV OK)"
                    r.isSuccessful -> "응답은 받았지만 WebDAV 헤더 없음: 포트/URL 확인 (DSM 5000이 아닌 WebDAV 5005/5006)"
                    else -> "서버 응답 ${r.code}"
                }
            }
        } catch (e: Exception) {
            "연결 실패: ${e.javaClass.simpleName} - ${e.message}"
        }
        const val KEY_PATH = "path"
        const val KEY_MIME = "mime"

        fun guessMime(path: String): String {
            val ext = path.substringAfterLast('.', "").lowercase()
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        }

        fun enqueue(ctx: Context, file: File, mime: String? = null) {
            val prefs = Prefs(ctx)
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (prefs.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .build()
            val work = OneTimeWorkRequestBuilder<UploadWorker>()
                .setConstraints(constraints)
                .setInputData(workDataOf(KEY_PATH to file.absolutePath, KEY_MIME to (mime ?: guessMime(file.name))))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag(TAG)
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(file.name, ExistingWorkPolicy.KEEP, work)
        }
    }
}
