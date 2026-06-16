package com.example

import java.text.Collator
import java.util.Locale

/**
 * 应用排序工具类
 * 使用系统自带 java.text.Collator，无需第三方依赖。
 * Collator.getInstance(Locale.CHINESE) 会根据汉字的拼音读音进行排序，
 * 例如：爱奇艺 < 百度 < 抖音 < 微信 < 支付宝
 */
object AppSortUtils {

    val chineseCollator: Collator by lazy {
        Collator.getInstance(Locale.CHINESE).apply {
            strength = Collator.PRIMARY
        }
    }

    /** 按拼音字母序比较两个 AppModel（用于 sortedWith） */
    fun getAlphaComparator(): Comparator<AppModel> =
        Comparator { a, b -> chineseCollator.compare(a.label, b.label) }
}