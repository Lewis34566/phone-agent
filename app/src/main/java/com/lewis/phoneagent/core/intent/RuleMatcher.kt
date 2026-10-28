package com.lewis.phoneagent.core.intent

/**
 * 本地意图匹配的共享辅助函数：关键词打分 + 常见文本模式提取。
 * 各工具的 match() 用它拼出自己专属的匹配逻辑，Dispatcher 只取最高分。
 */
object RuleMatcher {

    /**
     * 关键词打分：
     *  - 命中 strong 词 → 0.9（高置信，直接执行）
     *  - 只命中 weak 词 → 0.7（中等置信，仍走本地，避免误伤其他工具）
     *  - 都不命中 → null（本工具不认这句话）
     */
    fun keywordScore(text: String, strong: List<String>, weak: List<String> = emptyList()): Float? {
        if (strong.any { text.contains(it) }) return 0.9f
        if (weak.any { text.contains(it) }) return 0.7f
        return null
    }

    /**
     * 从"查看微信的权限 / 微信有哪些权限 / 微信的权限列表"这类句子里提取应用名。
     * 返回 null 表示句式里没带应用名。
     */
    private val PERMISSION_NAME_PATTERNS = listOf(
        Regex("(?:查看|看|检查|查|问问)?\\s*([\\u4e00-\\u9fa5A-Za-z0-9+_. ]{1,20}?)(?:的|有哪些|有什么|都有什么|都有哪些)(?:权限|权限列表|权限情况)"),
        Regex("(?:权限|权限列表|权限情况)(?:怎么|如何|是|有)?(?:看|查|查看)?\\s*([\\u4e00-\\u9fa5A-Za-z0-9+_.]{1,20})"),
    )

    fun extractAppNameFromPermissionQuery(text: String): String? {
        for (p in PERMISSION_NAME_PATTERNS) {
            val m = p.find(text) ?: continue
            val name = m.groupValues[1].trim()
            if (name.isNotEmpty()) return name
        }
        return null
    }

    /** 从"很久没用的应用/3个月没用/半年没用"里解析天数，默认 30 */
    fun parseUnusedDays(text: String): Int = when {
        text.contains("半年") -> 180
        text.contains("三个月") || text.contains("3个月") || text.contains("一个季度") -> 90
        text.contains("两个月") || text.contains("2个月") -> 60
        text.contains("一个月") || text.contains("1个月") || text.contains("30天") -> 30
        text.contains("一周") || text.contains("7天") -> 7
        else -> 30
    }

    /**
     * 从动作句里提取目标应用名："打开微信"→微信，"卸载 扣扣"→扣扣，
     * "打开微信的设置"→微信（尾部修饰词剥掉）。
     * verbs 由各工具传入自己的动作词表；未找到返回 null。
     */
    fun extractActionTarget(text: String, verbs: List<String>): String? {
        for (v in verbs) {
            val idx = text.indexOf(v)
            if (idx < 0) continue
            var rest = text.substring(idx + v.length)
            // 剥掉前导语气词："一下/帮我/给我/这个"
            rest = rest.replace(Regex("^(一下|帮我|给我|这个|那个)\\s*"), "")
            // 剥掉尾部修饰："的设置/设置页/详情/权限/缓存/存储/这个应用/软件/app"
            rest = rest.replace(
                Regex(
                    "\\s*(的(设置|设置页|详情|权限|权限列表|缓存|存储|存储空间|应用信息)" +
                        "|这个应用|这个软件|这个app|这个App|应用|软件|app|App)?\\s*$",
                ),
                "",
            )
            val target = rest.trim()
            if (target.isNotEmpty() && target.length <= 20) return target
        }
        return null
    }
}
