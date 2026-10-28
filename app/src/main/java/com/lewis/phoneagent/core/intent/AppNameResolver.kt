package com.lewis.phoneagent.core.intent

import com.lewis.phoneagent.data.AppRepository
import com.lewis.phoneagent.model.AppInfo

/**
 * 应用名解析器：把用户嘴里说的名字（"微信"、"扣扣"）映射到真实安装的应用。
 *
 * 三级匹配：
 *  1. 精确匹配（label 完全一致）
 *  2. 包含匹配（"微信" ⊂ "微信读书"，或反向）
 *  3. 编辑距离 ≤ 2（容错错别字/同音字）
 *
 * 返回候选列表（可能 0~多个）；工具层负责处理歧义：
 * 0 个 → 提示找不到；多个 → 列出候选让用户说准确名字。
 * 整个过程纯本地、读缓存，不经过大模型。
 */
class AppNameResolver(private val repository: AppRepository) {

    suspend fun resolve(name: String): List<AppInfo> {
        val n = name.trim()
        if (n.isEmpty()) return emptyList()
        val apps = repository.apps()

        // 1. 精确匹配
        apps.filter { it.label == n }.let { if (it.isNotEmpty()) return it }

        // 2. 包含匹配（双向）
        val contains = apps.filter { it.label.contains(n) || n.contains(it.label) }
        if (contains.isNotEmpty()) return contains

        // 3. 编辑距离 ≤ 2
        return apps.filter { levenshtein(it.label, n) <= 2 }.take(5)
    }

    /** 经典 Levenshtein 距离（动态规划，名字都很短，性能无压力） */
    private fun levenshtein(a: String, b: String): Int {
        val m = a.length
        val n = b.length
        if (m == 0) return n
        if (n == 0) return m
        val dp = IntArray(n + 1) { it }
        for (i in 1..m) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..n) {
                val tmp = dp[j]
                dp[j] = minOf(
                    dp[j] + 1,        // 删除
                    dp[j - 1] + 1,    // 插入
                    prev + if (a[i - 1] == b[j - 1]) 0 else 1, // 替换
                )
                prev = tmp
            }
        }
        return dp[n]
    }
}
