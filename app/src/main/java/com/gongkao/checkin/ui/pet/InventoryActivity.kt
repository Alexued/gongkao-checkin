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
import androidx.recyclerview.widget.GridLayoutManager
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

data class InventoryCategoryDisplay(
    val category: String,
    val name: String,
    val items: List<Pair<ShopItem, Int>>
)

class InventoryActivity : AppCompatActivity() {

    private val onChange = { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_inventory)
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
        val categories = mutableListOf<InventoryCategoryDisplay>()

        PetShop.allCategories().forEach { (catKey, pair) ->
            val catName = pair.first
            val allItems = pair.second
            val unlockedIds = petData.unlocked[catKey] ?: emptyList()

            val ownedItems = mutableListOf<Pair<ShopItem, Int>>()

            unlockedIds.forEach { id ->
                val item = allItems.firstOrNull { it.id == id }
                if (item != null) {
                    val count = if (catKey == "foods") {
                        petData.foodInventory[id] ?: 0
                    } else {
                        1
                    }
                    ownedItems.add(Pair(item, count))
                }
            }

            if (ownedItems.isNotEmpty()) {
                categories.add(InventoryCategoryDisplay(catKey, catName, ownedItems))
            }
        }

        findViewById<RecyclerView>(R.id.recycler).adapter = CategoryAdapter(categories)
    }

    inner class CategoryAdapter(private val categories: List<InventoryCategoryDisplay>) :
        RecyclerView.Adapter<CategoryAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val title: TextView = v.findViewById(R.id.categoryTitle)
            val grid: RecyclerView = v.findViewById(R.id.itemGrid)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_inventory_category, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(h: VH, pos: Int) {
            val cat = categories[pos]
            h.title.text = cat.name
            h.grid.layoutManager = GridLayoutManager(this@InventoryActivity, 4)
            h.grid.adapter = ItemAdapter(cat.items)
        }

        override fun getItemCount() = categories.size
    }

    inner class ItemAdapter(private val items: List<Pair<ShopItem, Int>>) :
        RecyclerView.Adapter<ItemAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val icon: TextView = v.findViewById(R.id.itemIcon)
            val name: TextView = v.findViewById(R.id.itemName)
            val count: TextView = v.findViewById(R.id.itemCount)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_inventory_item, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(h: VH, pos: Int) {
            val (item, count) = items[pos]
            h.icon.text = item.icon
            h.name.text = item.name
            h.count.text = "x$count"

            h.itemView.tap {
                // 根据物品类型播放对应动画
                val animName = when {
                    item.id == "walk" -> "walk"
                    item.id == "run" -> "run"
                    item.id == "celebrate" -> "celebrate"
                    item.id == "think" -> "think"
                    item.id in listOf("apple", "banana", "orange", "grape", "watermelon",
                                       "cake", "cookie", "candy", "icecream", "pizza") -> "eating"
                    else -> "happy"
                }

                // 通过广播通知 PetActivity 播放动画
                val intent = Intent("com.gongkao.checkin.PET_ANIMATION")
                intent.putExtra("animation", animName)
                sendBroadcast(intent)

                toast("${item.name} 使用中")
            }
        }

        override fun getItemCount() = items.size
    }

    companion object {
        fun start(ctx: Context) {
            ctx.startActivity(Intent(ctx, InventoryActivity::class.java))
        }
    }
}
