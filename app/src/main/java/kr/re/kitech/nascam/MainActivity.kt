package kr.re.kitech.nascam

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kr.re.kitech.nascam.databinding.ActivityMainBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding
    private var imageCapture: ImageCapture? = null

    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startCamera() else Toast.makeText(this, "카메라 권한이 필요합니다", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.btnSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        b.btnCapture.setOnClickListener { takePhoto() }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
            startCamera() else requestPermission.launch(Manifest.permission.CAMERA)

        // 업로드 대기/진행 건수 표시
        WorkManager.getInstance(this).getWorkInfosByTagLiveData(UploadWorker.TAG).observe(this) { infos ->
            val pending = infos.count { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
            val failed = infos.count { it.state == WorkInfo.State.FAILED }
            updateStatus(pending, failed)
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus(0, 0)
    }

    private fun updateStatus(pending: Int, failed: Int) {
        val prefs = Prefs(this)
        b.status.text = when {
            !prefs.isConfigured -> "NAS 설정을 먼저 입력하세요"
            pending > 0 && prefs.lastError.isNotBlank() -> "업로드 재시도 대기 $pending 장\n마지막 오류: ${prefs.lastError}"
            pending > 0 -> "업로드 대기 중: $pending 장" + if (prefs.wifiOnly) " (Wi-Fi 연결 필요)" else ""
            failed > 0 -> "업로드 실패 $failed 장\n${prefs.lastError}"
            else -> "NAS 연결 준비됨 · ${prefs.folder}"
        }
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.surfaceProvider = b.preview.surfaceProvider }
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()
            provider.unbindAll()
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return
        if (!Prefs(this).isConfigured) {
            Toast.makeText(this, "NAS 설정을 먼저 입력하세요", Toast.LENGTH_SHORT).show(); return
        }
        val dir = File(getExternalFilesDir("Pictures"), "NasCam").apply { mkdirs() }
        val name = "IMG_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".jpg"
        val file = File(dir, name)
        val opts = ImageCapture.OutputFileOptions.Builder(file).build()

        capture.takePicture(opts, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(r: ImageCapture.OutputFileResults) {
                UploadWorker.enqueue(this@MainActivity, file)
                Toast.makeText(this@MainActivity, "촬영 완료 · NAS 업로드 예약", Toast.LENGTH_SHORT).show()
            }
            override fun onError(e: ImageCaptureException) {
                Toast.makeText(this@MainActivity, "촬영 실패: ${e.message}", Toast.LENGTH_LONG).show()
            }
        })
    }
}
