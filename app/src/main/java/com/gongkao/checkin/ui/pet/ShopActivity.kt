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
import com.gongkao.checkin.ui.AppDialog
import com.gongkao.checkin.ui.edgeToEdge
import com.gongkao.checkin.ui.padTopInset
import com.gongkao.checkin.ui.tap
import com.gongkao.checkin.ui.toast

data class ShopCategoryDisplay(
    val category: String,
    val name: String,
    val items: List<ShopItem>
)

class ShopActivity : AppCompatActivity() {

    private val onChange = { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shop)
        edgeToEdge()

        findViewById<View>(R.id.topBar).padTopInset()
        findViewById<ImageView>(R.id.btnBack).tap { finish() }

        val categories = PetShop.allCategories().map { (cat, pair) ->
            ShopCategoryDisplay(cat, pair.first, pair.second)
        }

        val recycler = findViewById<RecyclerView>(R.id.recycler)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = CategoryAdapter(categories)

        Repo.addListener(onChange)
        refresh()
    }

    override fun onDestroy() {
        super.onDestroy()
        Repo.removeListener(onChange)
    }

    private fun refresh() {
        findViewById<TextView>(R.id.starCount).text = "⭐ ${Repo.petData().stars}"
        findViewById<RecyclerView>(R.id.recycler).adapter?.notifyDataSetChanged()
    }

    inner class CategoryAdapter(private val categories: List<ShopCategoryDisplay>) :
        RecyclerView.Adapter<CategoryAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val title: TextView = v.findViewById(R.id.categoryTitle)
            val grid: RecyclerView = v.findViewById(R.id.itemGrid)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_shop_category, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(h: VH, pos: Int) {
            val cat = categories[pos]
            h.title.text = cat.name
            h.grid.layoutManager = GridLayoutManager(this@ShopActivity, 4)
            h.grid.adapter = ItemAdapter(cat.items, cat.category)
        }

        override fun getItemCount() = categories.size
    }

    inner class ItemAdapter(private val items: List<ShopItem>, private val category: String) :
        RecyclerView.Adapter<ItemAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val icon: TextView = v.findViewById(R.id.itemIcon)
            val name: TextView = v.findViewById(R.id.itemName)
            val status: TextView = v.findViewById(R.id.itemStatus)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_shop_item, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(h: VH, pos: Int) {
            val item = items[pos]
            val petData = Repo.petData()
            val unlocked = petData.unlocked[category]?.contains(item.id) == true

            h.icon.text = item.icon
            h.name.text = item.name

            if (unlocked) {
                val slot = PetShop.getSlotForCategory(category)
                val equipped = petData.equipped[slot] == item.id
                h.status.text = if (equipped) "使用中" else "装备"
                h.status.setTextColor(getColor(R.color.accent))
                h.itemView.tap {
                    if (!equipped) {
                        Repo.equipPetItem(slot, item.id)
                        toast("已装备")
                    }
                }
            } else {
                h.status.text = "⭐ ${item.cost}"
                h.status.setTextColor(getColor(R.color.ink_sub))
                h.itemView.tap {
                    val currentStars = Repo.petData().stars
                    if (currentStars < item.cost) {
                        toast("星星不足，还需要 ${item.cost - currentStars} 颗星星")
                        return@tap
                    }
                    AppDialog.show(
                        ctx = this@ShopActivity,
                        title = "购买物品",
                        message = "确定花费 ${item.cost} 颗星星购买「${item.name}」吗？",
                        positive = "购买",
                        negative = "取消"
                    ) {
                        if (Repo.unlockPetItem(category, item.id, item.cost)) {
                            toast("购买成功！")
                            refresh()
                        } else {
                            toast("星星不足")
                        }
                    }
                }
            }
        }

        override fun getItemCount() = items.size
    }

    companion object {
        fun start(ctx: Context) {
            ctx.startActivity(Intent(ctx, ShopActivity::class.java))
        }
    }
}
