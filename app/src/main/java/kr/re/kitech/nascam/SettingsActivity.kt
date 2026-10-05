package kr.re.kitech.nascam

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.re.kitech.nascam.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val b = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(b.root)
        val prefs = Prefs(this)

        b.url.setText(prefs.url)
        b.folder.setText(prefs.folder)
        b.user.setText(prefs.user)
        b.pass.setText(prefs.pass)
        b.dateFolder.isChecked = prefs.dateFolder
        b.wifiOnly.isChecked = prefs.wifiOnly
        b.deleteAfter.isChecked = prefs.deleteAfter

        fun applyToPrefs() {
            prefs.url = b.url.text.toString()
            prefs.folder = b.folder.text.toString()
            prefs.user = b.user.text.toString()
            prefs.pass = b.pass.text.toString()
            prefs.dateFolder = b.dateFolder.isChecked
            prefs.wifiOnly = b.wifiOnly.isChecked
            prefs.deleteAfter = b.deleteAfter.isChecked
        }

        b.test.setOnClickListener {
            applyToPrefs()
            b.result.text = "테스트 중..."
            lifecycleScope.launch {
                val msg = withContext(Dispatchers.IO) { UploadWorker.testConnection(prefs) }
                b.result.text = msg
            }
        }

        b.save.setOnClickListener {
            prefs.url = b.url.text.toString()
            prefs.folder = b.folder.text.toString()
            prefs.user = b.user.text.toString()
            prefs.pass = b.pass.text.toString()
            prefs.dateFolder = b.dateFolder.isChecked
            prefs.wifiOnly = b.wifiOnly.isChecked
            prefs.deleteAfter = b.deleteAfter.isChecked
            Toast.makeText(this, "저장되었습니다", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
