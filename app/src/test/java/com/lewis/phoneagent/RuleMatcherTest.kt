package com.lewis.phoneagent

import com.lewis.phoneagent.core.intent.RuleMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 本地意图匹配规则单元测试（JVM，不依赖设备）。
 * 这些规则是全 App「<300ms 本地响应」的地基，改规则必须跑这里。
 */
class RuleMatcherTest {

    // ---- 关键词打分 ----

    @Test
    fun `强关键词命中得0_9分`() {
        assertEquals(0.9f, RuleMatcher.keywordScore("列出已装应用", listOf("已装应用")))
        assertEquals(0.9f, RuleMatcher.keywordScore("哪些应用很久没用了", listOf("很久没用")))
    }

    @Test
    fun `弱关键词命中得0_7分`() {
        assertEquals(0.7f, RuleMatcher.keywordScore("后台的东西", strong = listOf("后台活动"), weak = listOf("后台")))
    }

    @Test
    fun `无关键词返回null`() {
        assertNull(RuleMatcher.keywordScore("今天天气怎么样", listOf("已装应用")))
    }

    // ---- 动作目标提取 ----

    @Test
    fun `提取打开目标`() {
        assertEquals("微信", RuleMatcher.extractActionTarget("打开微信", listOf("打开")))
        assertEquals("微信", RuleMatcher.extractActionTarget("帮我打开 微信", listOf("打开")))
    }

    @Test
    fun `提取时剥掉尾部修饰词`() {
        assertEquals("微信", RuleMatcher.extractActionTarget("打开微信的设置", listOf("打开")))
        assertEquals("微信", RuleMatcher.extractActionTarget("卸载微信这个应用", listOf("卸载")))
    }

    @Test
    fun `无动词返回null`() {
        assertNull(RuleMatcher.extractActionTarget("微信", listOf("打开", "卸载")))
    }

    // ---- 权限句式的应用名提取 ----

    @Test
    fun `提取权限句式的应用名`() {
        assertEquals("微信", RuleMatcher.extractAppNameFromPermissionQuery("查看微信的权限"))
        assertEquals("微信", RuleMatcher.extractAppNameFromPermissionQuery("微信有哪些权限"))
        assertEquals("微信读书", RuleMatcher.extractAppNameFromPermissionQuery("微信读书的权限列表"))
    }

    @Test
    fun `无应用名的权限句返回null`() {
        assertNull(RuleMatcher.extractAppNameFromPermissionQuery("看看权限"))
    }

    // ---- 天数解析 ----

    @Test
    fun `解析未使用天数`() {
        assertEquals(180, RuleMatcher.parseUnusedDays("半年没用的应用"))
        assertEquals(90, RuleMatcher.parseUnusedDays("3个月没用"))
        assertEquals(30, RuleMatcher.parseUnusedDays("一个月没用"))
        assertEquals(7, RuleMatcher.parseUnusedDays("一周没用了"))
        assertEquals(30, RuleMatcher.parseUnusedDays("哪些应用很久没用了")) // 默认 30
    }

    // ---- 工具间不抢话（OpenApp vs AppSettings 的边界在工具 match 里，这里验证提取结果可区分） ----

    @Test
    fun `设置句式仍能提取出应用名`() {
        // 尾部修饰（的缓存/的设置）由提取器统一剥掉，工具层拿到的就是干净应用名
        assertEquals("微信", RuleMatcher.extractActionTarget("打开微信的缓存", listOf("打开")))
    }
}
