package com.gongkao.checkin.data

/** 宠物商店物品 */
data class ShopItem(
    val id: String,
    val icon: String,
    val name: String,
    val cost: Int
)

/** 宠物商店配置 */
object PetShop {
    val actions = listOf(
        ShopItem("walk", "🚶", "走路", 10),
        ShopItem("run", "🏃", "跑步", 20),
        ShopItem("jump", "🦘", "跳跃", 15),
        ShopItem("rope", "🪢", "跳绳", 25),
        ShopItem("climb", "🧗", "爬行", 20),
        ShopItem("read", "📖", "看书", 30),
        ShopItem("write", "✍️", "写字", 30),
        ShopItem("think", "🤔", "思考", 25),
        ShopItem("celebrate", "🎉", "庆祝", 35),
        ShopItem("dance", "💃", "跳舞", 40)
    )

    val outfits = listOf(
        ShopItem("hat1", "🎩", "礼帽", 50),
        ShopItem("hat2", "🎓", "学士帽", 60),
        ShopItem("hat3", "👑", "皇冠", 100),
        ShopItem("glasses1", "👓", "眼镜", 40),
        ShopItem("glasses2", "🕶️", "墨镜", 45),
        ShopItem("scarf", "🧣", "围巾", 35),
        ShopItem("tie", "👔", "领带", 40),
        ShopItem("shirt1", "👕", "T恤", 50),
        ShopItem("shirt2", "👔", "西装", 80),
        ShopItem("dress", "👗", "裙子", 70)
    )

    val foods = listOf(
        ShopItem("apple", "🍎", "苹果", 5),
        ShopItem("banana", "🍌", "香蕉", 5),
        ShopItem("orange", "🍊", "橙子", 5),
        ShopItem("grape", "🍇", "葡萄", 8),
        ShopItem("watermelon", "🍉", "西瓜", 10),
        ShopItem("cake", "🍰", "蛋糕", 15),
        ShopItem("cookie", "🍪", "饼干", 8),
        ShopItem("candy", "🍬", "糖果", 6),
        ShopItem("icecream", "🍦", "冰淇淋", 12),
        ShopItem("pizza", "🍕", "披萨", 20)
    )

    val backgrounds = listOf(
        ShopItem("grass", "🌱", "草地", 30),
        ShopItem("beach", "🏖️", "海滩", 40),
        ShopItem("mountain", "⛰️", "山地", 50),
        ShopItem("forest", "🌲", "森林", 45),
        ShopItem("city", "🏙️", "城市", 60),
        ShopItem("space", "🌌", "太空", 80),
        ShopItem("castle", "🏰", "城堡", 70),
        ShopItem("garden", "🏡", "花园", 50),
        ShopItem("snow", "❄️", "雪地", 55),
        ShopItem("desert", "🏜️", "沙漠", 45)
    )

    val states = listOf(
        ShopItem("sleep", "😴", "睡觉", 20),
        ShopItem("wash", "🚿", "洗脸", 25),
        ShopItem("bath", "🛁", "洗澡", 30),
        ShopItem("brush", "🪥", "刷牙", 20),
        ShopItem("exercise", "🏋️", "锻炼", 35),
        ShopItem("meditate", "🧘", "冥想", 40),
        ShopItem("music", "🎵", "听音乐", 30),
        ShopItem("paint", "🎨", "画画", 45),
        ShopItem("cook", "🍳", "做饭", 35),
        ShopItem("game", "🎮", "游戏", 40)
    )

    val accessories = listOf(
        ShopItem("bag", "🎒", "背包", 40),
        ShopItem("watch", "⌚", "手表", 50),
        ShopItem("phone", "📱", "手机", 60),
        ShopItem("book", "📚", "书籍", 35),
        ShopItem("umbrella", "☂️", "雨伞", 30),
        ShopItem("balloon", "🎈", "气球", 25),
        ShopItem("flower", "🌺", "花朵", 30),
        ShopItem("star", "⭐", "星星", 45),
        ShopItem("heart", "💝", "爱心", 40),
        ShopItem("gift", "🎁", "礼物", 50)
    )

    val vehicles = listOf(
        ShopItem("bike", "🚲", "自行车", 60),
        ShopItem("car", "🚗", "汽车", 100),
        ShopItem("plane", "✈️", "飞机", 150),
        ShopItem("rocket", "🚀", "火箭", 200),
        ShopItem("boat", "🚤", "快艇", 120),
        ShopItem("train", "🚆", "火车", 110),
        ShopItem("bus", "🚌", "巴士", 90),
        ShopItem("scooter", "🛴", "滑板车", 70),
        ShopItem("skateboard", "🛹", "滑板", 65),
        ShopItem("helicopter", "🚁", "直升机", 180)
    )

    fun allCategories() = mapOf(
        "actions" to Pair("动作", actions),
        "outfits" to Pair("服装", outfits),
        "foods" to Pair("食物", foods),
        "backgrounds" to Pair("背景", backgrounds),
        "states" to Pair("状态", states),
        "accessories" to Pair("配饰", accessories),
        "vehicles" to Pair("交通工具", vehicles)
    )

    fun findItem(category: String, id: String): ShopItem? {
        return when (category) {
            "actions" -> actions
            "outfits" -> outfits
            "foods" -> foods
            "backgrounds" -> backgrounds
            "states" -> states
            "accessories" -> accessories
            "vehicles" -> vehicles
            else -> null
        }?.firstOrNull { it.id == id }
    }

    fun getSlotForCategory(category: String): String = when (category) {
        "actions" -> "action"
        "outfits" -> "outfit"
        "backgrounds" -> "background"
        "states" -> "state"
        "accessories" -> "accessory"
        "vehicles" -> "vehicle"
        else -> category
    }
}
