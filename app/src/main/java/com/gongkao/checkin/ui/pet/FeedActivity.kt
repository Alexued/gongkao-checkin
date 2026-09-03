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
import com.gongkao.checkin.ui.tap
import com.gongkao.checkin.ui.toast

class FeedActivity : AppCompatActivity() {

    private val onChange = { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_feed)
        edgeToEdge()

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
        val ownedFood = PetShop.foods.filter { petData.foodInventory[it.id] ?: 0 > 0 }

        findViewById<RecyclerView>(R.id.recycler).adapter = FoodAdapter(ownedFood)
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

            h.icon.text = item.icon
            h.name.text = item.name
            h.count.text = "剩余 $count"

            h.itemView.tap {
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
