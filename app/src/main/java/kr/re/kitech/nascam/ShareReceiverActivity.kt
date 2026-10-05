package kr.re.kitech.nascam

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * 다른 앱의 "공유/보내기"로 넘어온 파일(이미지·동영상·문서·텍스트)을
 * 앱 저장소에 복사한 뒤 NAS 업로드 작업을 예약하고 즉시 종료한다.
 */
class ShareReceiverActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Prefs(this).isConfigured) {
            Toast.makeText(this, "NasCam 앱에서 NAS 설정을 먼저 입력하세요", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, SettingsActivity::class.java))
            finish(); return
        }

        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_SEND ->
                listOfNotNull(intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)
            Intent.ACTION_SEND_MULTIPLE ->
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.toList() ?: emptyList()
            else -> emptyList()
        }

        val dir = File(getExternalFilesDir("Shared"), "NasShare").apply { mkdirs() }
        var count = 0

        if (uris.isEmpty()) {
            // 파일 없이 텍스트(링크 등)만 공유된 경우 → .txt로 저장
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!text.isNullOrBlank()) {
                val name = "TEXT_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".txt"
                val f = File(dir, name).apply { writeText(text) }
                UploadWorker.enqueue(this, f, "text/plain")
                count = 1
            }
        } else {
            for (uri in uris) {
                try {
                    val name = uniqueName(dir, displayName(contentResolver, uri))
                    val f = File(dir, name)
                    contentResolver.openInputStream(uri)?.use { input ->
                        f.outputStream().use { input.copyTo(it) }
                    } ?: continue
                    UploadWorker.enqueue(this, f, contentResolver.getType(uri))
                    count++
                } catch (e: Exception) {
                    Toast.makeText(this, "복사 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        Toast.makeText(
            this,
            if (count > 0) "NAS 업로드 예약: ${count}개" else "저장할 항목이 없습니다",
            Toast.LENGTH_SHORT
        ).show()
        finish()
    }

    private fun displayName(cr: ContentResolver, uri: Uri): String {
        cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val n = c.getString(0)
                if (!n.isNullOrBlank()) return n
            }
        }
        val ext = android.webkit.MimeTypeMap.getSingleton()
            .getExtensionFromMimeType(cr.getType(uri) ?: "") ?: "bin"
        return "FILE_" + SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date()) + ".$ext"
    }

    /** 같은 이름이 이미 대기 중이면 (1), (2) ... 붙임 */
    private fun uniqueName(dir: File, name: String): String {
        if (!File(dir, name).exists()) return name
        val base = name.substringBeforeLast('.'); val ext = name.substringAfterLast('.', "")
        var i = 1
        while (true) {
            val n = if (ext.isEmpty()) "$base($i)" else "$base($i).$ext"
            if (!File(dir, n).exists()) return n
            i++
        }
    }
}
