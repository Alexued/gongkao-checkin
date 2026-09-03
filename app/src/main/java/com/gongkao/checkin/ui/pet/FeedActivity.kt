package com.gongkao.checkin.ui.pet

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gongkao.checkin.R
import com.gongkao.checkin.data.PetShop
import com.gongkao.checkin.data.Repo
import com.gongkao.checkin.data.ShopItem
import com.gongkao.checkin.ui.edgeToEdge
import com.gongkao.checkin.ui.padTopInset
import com.gongkao.checkin.ui.tap
import com.gongkao.checkin.ui.toast

class FeedActivity : AppCompatActivity() {

    private val onChange = { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_feed)
        edgeToEdge()

        findViewById<View>(R.id.topBar).padTopInset()
        findViewById<ImageView>(R.id.btnBack).tap { finish() }

        val recycler = findViewById<RecyclerView>(R.id.recycler)
        recycler.layoutManager = LinearLayoutManager(this)

        Repo.addListener(onChange)
        refresh()
    }

    override fun onDestroy() {
        super.onDestroy()
        Repo.removeListener(onChange)
    }

    private fun refresh() {
        val petData = Repo.petData()
        val allFoods = PetShop.foods
        val ownedFood = allFoods.filter { petData.foodInventory[it.id] ?: 0 > 0 }

        findViewById<RecyclerView>(R.id.recycler).adapter = FoodAdapter(if (ownedFood.isEmpty()) allFoods else ownedFood)
    }

    inner class FoodAdapter(private val items: List<ShopItem>) :
        RecyclerView.Adapter<FoodAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val icon: TextView = v.findViewById(R.id.foodIcon)
            val name: TextView = v.findViewById(R.id.foodName)
            val count: TextView = v.findViewById(R.id.foodCount)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_food, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(h: VH, pos: Int) {
            val item = items[pos]
            val petData = Repo.petData()
            val count = petData.foodInventory[item.id] ?: 0
            val owned = count > 0

            h.icon.text = item.icon
            h.name.text = item.name
            h.count.text = if (owned) "剩余 $count" else "未拥有"

            h.itemView.tap {
                if (!owned) {
                    toast("需要先在商店购买")
                    return@tap
                }
                if (Repo.feedPet(item.id)) {
                    toast("喂养成功")
                    refresh()
                } else {
                    toast("食物不足")
                }
            }
        }

        override fun getItemCount() = items.size
    }

    companion object {
        fun start(ctx: Context) {
            ctx.startActivity(Intent(ctx, FeedActivity::class.java))
        }
    }
}
