package com.jishi.collection

/**
 * 固定粗分类定义。
 *
 * fissionHint 是该粗分类的裂变约束提示词：为空表示该分类不参与裂变；
 * 非空时会拼进大模型的裂变 prompt，约束细分分类的命名方式（如旅游按目的地、探店按商圈）。
 */
data class CoarseCategoryDef(
    val id: String,
    val name: String,
    val description: String,
    val fissionHint: String,
)

/**
 * 小红书收藏笔记的固定粗分类体系。
 *
 * 类目取自小红书主流内容垂类：美食（做饭/探店）、旅行、穿搭、美妆、家居家装、
 * 健身、学习、职场、母婴、宠物、数码、影视、好物推荐、健康养生、情感、摄影。
 */
object CoarseTaxonomy {
    val categories: List<CoarseCategoryDef> = listOf(
        CoarseCategoryDef(
            id = "cat_coarse_cooking",
            name = "做饭料理",
            description = "菜谱、烹饪教程、烘焙、家常菜、饮品制作",
            fissionHint = "按菜系或菜品类型细分，分类名示例：家常菜、烘焙甜品、减脂餐、川菜",
        ),
        CoarseCategoryDef(
            id = "cat_coarse_food_explore",
            name = "美食探店",
            description = "餐厅、咖啡店、小吃、酒吧等线下探店与推荐",
            fissionHint = "按城市或商圈区域细分，分类名示例：王府井探店、三里屯咖啡、成都探店",
        ),
        CoarseCategoryDef(
            id = "cat_coarse_travel",
            name = "旅游出行",
            description = "旅行攻略、景点推荐、酒店民宿、行程路线、周边游",
            fissionHint = "按旅行目的地细分，分类名示例：新疆旅游、青岛旅游、日本旅游",
        ),
        CoarseCategoryDef(
            id = "cat_coarse_outfit",
            name = "穿搭",
            description = "服装搭配、鞋包配饰、穿搭灵感",
            fissionHint = "按风格、季节或场景细分，分类名示例：通勤穿搭、夏季穿搭、小个子穿搭",
        ),
        CoarseCategoryDef(
            id = "cat_coarse_beauty",
            name = "美妆护肤",
            description = "彩妆、护肤、发型、美甲、香水",
            fissionHint = "按彩妆、护肤、发型、美甲等主题细分，分类名示例：底妆技巧、抗老护肤、编发教程",
        ),
        CoarseCategoryDef(
            id = "cat_coarse_fitness",
            name = "健身锻炼",
            description = "健身训练、瑜伽、跑步、减脂塑形、运动教程",
            fissionHint = "按运动类型或训练部位细分，分类名示例：瑜伽拉伸、腹肌训练、跑步入门",
        ),
        CoarseCategoryDef(
            id = "cat_coarse_home",
            name = "装修家居",
            description = "装修设计、软装布置、收纳整理、家电选购",
            fissionHint = "按空间或装修主题细分，分类名示例：客厅装修、厨房收纳、奶油风装修",
        ),
        CoarseCategoryDef(
            id = "cat_coarse_study",
            name = "学习成长",
            description = "学习方法、考试考证、语言学习、读书笔记、技能教程",
            fissionHint = "按学科或技能细分，分类名示例：英语学习、考研备考、PPT技巧",
        ),
        CoarseCategoryDef(
            id = "cat_coarse_career",
            name = "职场",
            description = "求职面试、简历、职场经验、副业搞钱",
            fissionHint = "按职场主题细分，分类名示例：简历面试、职场沟通、副业赚钱",
        ),
        CoarseCategoryDef(
            id = "cat_coarse_baby",
            name = "母婴亲子",
            description = "备孕育儿、辅食、早教、亲子活动",
            fissionHint = "按孩子年龄段或育儿主题细分，分类名示例：辅食制作、早教启蒙、亲子游戏",
        ),
        CoarseCategoryDef(
            id = "cat_coarse_pet",
            name = "宠物",
            description = "养猫养狗等宠物饲养、宠物用品、宠物健康",
            fissionHint = "按宠物种类或养宠主题细分，分类名示例：养猫日常、狗狗训练、宠物健康",
        ),
        CoarseCategoryDef(
            id = "cat_coarse_digital",
            name = "数码科技",
            description = "手机、电脑、相机等数码产品测评与使用技巧、软件工具",
            fissionHint = "按产品品类细分，分类名示例：手机测评、相机摄影装备、效率软件",
        ),
        CoarseCategoryDef(
            id = "cat_coarse_entertainment",
            name = "影视娱乐",
            description = "电影剧集推荐、综艺、音乐、游戏、明星八卦",
            fissionHint = "按内容类型细分，分类名示例：电影推荐、追剧清单、游戏攻略",
        ),
        CoarseCategoryDef(
            id = "cat_coarse_shopping",
            name = "好物推荐",
            description = "购物分享、好物清单、平价替代、开箱种草",
            fissionHint = "按商品品类细分，分类名示例：家居好物、数码好物、平价好物",
        ),
        CoarseCategoryDef(
            id = "cat_coarse_health",
            name = "健康养生",
            description = "养生知识、饮食健康、睡眠调理、中医理疗",
            fissionHint = "按养生主题细分，分类名示例：中医养生、睡眠改善、饮食调理",
        ),
        CoarseCategoryDef(
            id = "cat_coarse_emotion",
            name = "情感心理",
            description = "恋爱关系、人际交往、心理成长、生活感悟",
            fissionHint = "按情感主题细分，分类名示例：恋爱关系、自我成长、人际关系",
        ),
        CoarseCategoryDef(
            id = "cat_coarse_photography",
            name = "摄影拍照",
            description = "拍照姿势、摄影技巧、修图教程、拍照机位",
            fissionHint = "按题材或技巧细分，分类名示例：人像摄影、修图教程、旅拍机位",
        ),
        CoarseCategoryDef(
            id = "cat_coarse_other",
            name = "其他",
            description = "无法归入以上任何分类的笔记",
            fissionHint = "",
        ),
    )

    val byId: Map<String, CoarseCategoryDef> = categories.associateBy { it.id }
}
