package com.gongkao.checkin.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.gongkao.checkin.R
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/**
 * 展示本机的同步二维码：把 ip/port/pin 编码成 gkc://sync?ip=..&port=..&pin=.. ，
 * 对方在「扫描对方的二维码」里扫到后直接解析出连接信息，不用手输 PIN。
 */
object QrShowDialog {

    fun show(ctx: Context, ip: String, port: Int, pin: String) {
        val content = "gkc://sync?ip=$ip&port=$port&pin=$pin"
        val bmp = encode(content, 480) ?: return

        val v = ctx.inflate(R.layout.dialog_qr, null)
        val scrim = v.findViewById<View>(R.id.dialogScrim)
        val card = v.findViewById<View>(R.id.dialogCard)
        v.findViewById<TextView>(R.id.dialogTitle).text = ctx.getString(R.string.device_sync_show_qr)
        v.findViewById<ImageView>(R.id.qrImage).setImageBitmap(bmp)
        v.findViewById<TextView>(R.id.dialogMessage).text = "$ip:$port"
        val btnClose = v.findViewById<TextView>(R.id.dialogPositive)
        btnClose.text = ctx.getString(R.string.got_it)

        val d = Popup.dialog(ctx, v)
        Popup.wireDismiss(d, scrim, card)
        btnClose.tap { Popup.close(d) }

        d.show()
        Popup.enter(scrim, card)
    }

    private fun encode(content: String, size: Int): Bitmap? = runCatching {
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        bmp
    }.getOrNull()
}
