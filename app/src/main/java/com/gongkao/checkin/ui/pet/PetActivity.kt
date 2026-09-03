package com.gongkao.checkin.ui.pet

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.gongkao.checkin.R
import com.gongkao.checkin.data.PetShop
import com.gongkao.checkin.data.Repo
import com.gongkao.checkin.ui.AppDialog
import com.gongkao.checkin.ui.edgeToEdge
import com.gongkao.checkin.ui.padTopInset
import com.gongkao.checkin.ui.tap
import com.gongkao.checkin.view.PetView

class PetActivity : AppCompatActivity() {

    private val onChange = { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pet)
        edgeToEdge()

        findViewById<View>(R.id.topBar).padTopInset()
        findViewById<ImageView>(R.id.btnBack).tap { finish() }

        val petView = findViewById<PetView>(R.id.petView)
        val petName = findViewById<EditText>(R.id.petName)
        val starCount = findViewById<TextView>(R.id.starCount)
        val statsStars = findViewById<TextView>(R.id.statsStars)
        val statsTotalEarned = findViewById<TextView>(R.id.statsTotalEarned)
        val statsUnlocked = findViewById<TextView>(R.id.statsUnlocked)
        val equippedAction = findViewById<TextView>(R.id.equippedAction)
        val equippedEyes = findViewById<TextView>(R.id.equippedEyes)
        val equippedAccessory = findViewById<TextView>(R.id.equippedAccessory)
        val equippedBackground = findViewById<TextView>(R.id.equippedBackground)

        // 长按星星数进入测试模式调整星星
        starCount.setOnLongClickListener {
            AppDialog.showInput(
                ctx = this,
                title = "测试模式",
                message = "设置星星数量（当前：${Repo.petData().stars}）",
                inputHint = "星星数量",
                inputPrefill = Repo.petData().stars.toString(),
                positive = "确定",
                negative = "取消",
                onConfirm = { input ->
                    val count = input.toIntOrNull()
                    if (count != null && count >= 0) {
                        Repo.setPetStars(count)
                    }
                }
            )
            true
        }

        findViewById<TextView>(R.id.btnShop).tap {
            startActivity(Intent(this, ShopActivity::class.java))
        }

        findViewById<TextView>(R.id.btnFeed).tap {
            startActivity(Intent(this, FeedActivity::class.java))
        }

        findViewById<TextView>(R.id.btnInventory).tap {
            startActivity(Intent(this, InventoryActivity::class.java))
        }

        petName.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val name = petName.text.toString().trim()
                if (name.isNotEmpty()) {
                    Repo.setPetName(name)
                }
            }
        }

        Repo.addListener(onChange)
        refresh()
    }

    override fun onDestroy() {
        super.onDestroy()
        Repo.removeListener(onChange)
    }

    private fun refresh() {
        val petData = Repo.petData()

        findViewById<PetView>(R.id.petView).setPetData(petData)
        findViewById<EditText>(R.id.petName).setText(petData.name)
        findViewById<TextView>(R.id.starCount).text = "⭐ ${petData.stars}"
        findViewById<TextView>(R.id.statsStars).text = "⭐ ${petData.stars}"
        findViewById<TextView>(R.id.statsTotalEarned).text = "⭐ ${petData.totalEarned}"

        val totalUnlocked = petData.unlocked.values.sumOf { it.size }
        findViewById<TextView>(R.id.statsUnlocked).text = "$totalUnlocked 个"

        // 更新装备显示
        val actionId = petData.equipped["action"]
        val actionItem = if (actionId != null) PetShop.findItem("actions", actionId) else null
        findViewById<TextView>(R.id.equippedAction).text = actionItem?.name ?: "站立"

        val outfitId = petData.equipped["outfit"]
        val outfitItem = if (outfitId != null) PetShop.findItem("outfits", outfitId) else null
        findViewById<TextView>(R.id.equippedEyes).text = outfitItem?.name ?: "默认"

        val accessoryId = petData.equipped["accessory"]
        val accessoryItem = if (accessoryId != null) PetShop.findItem("accessories", accessoryId) else null
        findViewById<TextView>(R.id.equippedAccessory).text = accessoryItem?.name ?: "无"

        val backgroundId = petData.equipped["background"]
        val backgroundItem = if (backgroundId != null) PetShop.findItem("backgrounds", backgroundId) else null
        findViewById<TextView>(R.id.equippedBackground).text = backgroundItem?.name ?: "默认"
    }

    companion object {
        fun start(ctx: Context) {
            ctx.startActivity(Intent(ctx, PetActivity::class.java))
        }
    }
}
