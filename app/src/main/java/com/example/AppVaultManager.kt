package com.example

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest

/**
 * App Vault 数据持久层
 * - 密码以 SHA-256 hash 存储，绝不明文
 * - 密保问答以竖线分隔存储
 * - 直接复用 LauncherPrefs 的 hiddenPackages 字段
 */
class AppVaultManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("jiuyi_vault_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_PASSWORD_HASH = "vault_password_hash"
        private const val KEY_SECURITY_QUESTIONS = "vault_security_questions_v1"

        fun sha256(input: String): String {
            val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }

    // ── 密码 ──────────────────────────────────────────────────────────────────

    val hasPassword: Boolean
        get() = prefs.getString(KEY_PASSWORD_HASH, "").orEmpty().isNotEmpty()

    fun setPassword(raw: String) {
        prefs.edit().putString(KEY_PASSWORD_HASH, sha256(raw)).apply()
    }

    fun verifyPassword(raw: String): Boolean =
        sha256(raw) == prefs.getString(KEY_PASSWORD_HASH, "")

    fun clearPassword() {
        prefs.edit().remove(KEY_PASSWORD_HASH).apply()
    }

    // ── 密保问答 ──────────────────────────────────────────────────────────────

    /** 存储格式：问题1:::答案1|问题2:::答案2|问题3:::答案3 */
    fun saveSecurityQuestions(list: List<Pair<String, String>>) {
        val serialized = list
            .filter { it.first.isNotBlank() && it.second.isNotBlank() }
            .joinToString("|") { "${it.first}:::${it.second}" }
        prefs.edit().putString(KEY_SECURITY_QUESTIONS, serialized).apply()
    }

    fun getSecurityQuestions(): List<Pair<String, String>> {
        val raw = prefs.getString(KEY_SECURITY_QUESTIONS, "").orEmpty()
        if (raw.isEmpty()) return emptyList()
        return raw.split("|").mapNotNull { entry ->
            val parts = entry.split(":::")
            if (parts.size >= 2) parts[0] to parts[1] else null
        }
    }

    fun hasSecurityQuestions(): Boolean = getSecurityQuestions().isNotEmpty()

    fun clearSecurityQuestions() {
        prefs.edit().remove(KEY_SECURITY_QUESTIONS).apply()
    }

    /** 验证密保：用户提供的 answers 与存储的答案逐条比较，至少 1 条完全匹配即通过 */
    fun verifySecurityAnswers(provided: List<Pair<String, String>>): Boolean {
        val stored = getSecurityQuestions()
        return provided.any { (q, a) ->
            stored.any { (sq, sa) ->
                sq == q && sa.trim().equals(a.trim(), ignoreCase = true)
            }
        }
    }

    // ── 全部清除（重置） ──────────────────────────────────────────────────────

    fun clearAll() {
        clearPassword()
        clearSecurityQuestions()
    }
}