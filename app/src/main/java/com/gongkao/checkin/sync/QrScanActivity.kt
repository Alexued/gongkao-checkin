package com.gongkao.checkin.sync

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import com.gongkao.checkin.R
import com.gongkao.checkin.ui.edgeToEdge
import com.gongkao.checkin.ui.padBottomInset
import com.gongkao.checkin.ui.padTopInset
import com.gongkao.checkin.ui.tap
import com.gongkao.checkin.ui.toast

/**
 * 自绘扫码页：用 zxing-android-embedded 的 [DecoratedBarcodeView] 做取景框，
 * 外壳（顶栏、返回按钮、提示文案）全部是本 app 自己的布局，不用它自带的 CaptureActivity 界面。
 *
 * 扫到内容后按 gkc://sync?ip=..&port=..&pin=.. 解析，把结果放进 Intent extra 传回调用方。
 */
class QrScanActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_IP = "ip"
        const val EXTRA_PORT = "port"
        const val EXTRA_PIN = "pin"
    }

    private lateinit var barcodeView: DecoratedBarcodeView
    private var handled = false

    private val requestCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startScan() else {
            toast(getString(R.string.device_sync_fail))
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        edgeToEdge()
        setContentView(R.layout.activity_qr_scan)

        val topBar = findViewById<android.view.View>(R.id.topBar)
        topBar.padTopInset()
        topBar.findViewById<android.widget.TextView>(R.id.barTitle).text =
            getString(R.string.device_sync_scan_qr)
        topBar.findViewById<android.widget.ImageView>(R.id.btnBack).tap { finish() }
        findViewById<android.view.View>(R.id.scanHint).padBottomInset()

        barcodeView = findViewById(R.id.barcodeView)
        barcodeView.barcodeView.decoderFactory = DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE))

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startScan()
        } else {
            requestCamera.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startScan() {
        barcodeView.decodeContinuous(object : BarcodeCallback {
            override fun barcodeResult(result: BarcodeResult) {
                if (handled) return
                val text = result.text ?: return
                val parsed = parse(text) ?: return
                handled = true
                barcodeView.pause()
                val data = android.content.Intent().apply {
                    putExtra(EXTRA_IP, parsed.first)
                    putExtra(EXTRA_PORT, parsed.second)
                    putExtra(EXTRA_PIN, parsed.third)
                }
                setResult(RESULT_OK, data)
                finish()
            }

            override fun possibleResultPoints(resultPoints: MutableList<ResultPoint>) {}
        })
    }

    /** 解析 gkc://sync?ip=..&port=..&pin=.. ，格式不对返回 null。 */
    private fun parse(text: String): Triple<String, Int, String>? = runCatching {
        val uri = Uri.parse(text)
        if (uri.scheme != "gkc" || uri.host != "sync") return null
        val ip = uri.getQueryParameter("ip") ?: return null
        val port = uri.getQueryParameter("port")?.toIntOrNull() ?: return null
        val pin = uri.getQueryParameter("pin") ?: ""
        Triple(ip, port, pin)
    }.getOrNull()

    override fun onResume() {
        super.onResume()
        if (!handled) runCatching { barcodeView.resume() }
    }

    override fun onPause() {
        runCatching { barcodeView.pause() }
        super.onPause()
    }
}
