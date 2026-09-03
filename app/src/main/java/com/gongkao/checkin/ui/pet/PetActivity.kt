package com.gongkao.checkin.ui.pet

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.gongkao.checkin.R
import com.gongkao.checkin.data.Repo
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

        findViewById<ImageView>(R.id.btnBack).tap { finish() }

        val petView = findViewById<PetView>(R.id.petView)
        val petName = findViewById<EditText>(R.id.petName)
        val starCount = findViewById<TextView>(R.id.starCount)
        val statsStars = findViewById<TextView>(R.id.statsStars)
        val statsTotalEarned = findViewById<TextView>(R.id.statsTotalEarned)
        val statsUnlocked = findViewById<TextView>(R.id.statsUnlocked)

        findViewById<TextView>(R.id.btnShop).tap {
            startActivity(Intent(this, ShopActivity::class.java))
        }

        findViewById<TextView>(R.id.btnFeed).tap {
            startActivity(Intent(this, FeedActivity::class.java))
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
    }

    companion object {
        fun start(ctx: Context) {
            ctx.startActivity(Intent(ctx, PetActivity::class.java))
        }
    }
}
