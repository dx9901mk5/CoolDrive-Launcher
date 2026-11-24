package com.dxcar.launcher

import com.dxcar.launcher.data.AppLanguage
import com.dxcar.launcher.i18n.DxCarI18n
import com.dxcar.launcher.i18n.EffectiveLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class DxCarI18nTest {

    @Test
    fun `resolveEffective maps all explicit AppLanguage values accurately`() {
        assertEquals(EffectiveLanguage.ZH_TW, DxCarI18n.resolveEffective(AppLanguage.ZH_TW))
        assertEquals(EffectiveLanguage.ZH_CN, DxCarI18n.resolveEffective(AppLanguage.ZH_CN))
        assertEquals(EffectiveLanguage.EN, DxCarI18n.resolveEffective(AppLanguage.EN))
        assertEquals(EffectiveLanguage.JA, DxCarI18n.resolveEffective(AppLanguage.JA))
    }

    @Test
    fun `setConfiguredLanguage updates tr output and speech tags`() {
        DxCarI18n.setConfiguredLanguage(AppLanguage.ZH_TW)
        assertEquals("zh-TW", DxCarI18n.speechLanguageTag())
        assertEquals("繁", DxCarI18n.tr("繁", "简", "EN", "JA"))

        DxCarI18n.setConfiguredLanguage(AppLanguage.ZH_CN)
        assertEquals("zh-CN", DxCarI18n.speechLanguageTag())
        assertEquals("简", DxCarI18n.tr("繁", "简", "EN", "JA"))

        DxCarI18n.setConfiguredLanguage(AppLanguage.EN)
        assertEquals("en-US", DxCarI18n.speechLanguageTag())
        assertEquals("EN", DxCarI18n.tr("繁", "简", "EN", "JA"))

        DxCarI18n.setConfiguredLanguage(AppLanguage.JA)
        assertEquals("ja-JP", DxCarI18n.speechLanguageTag())
        assertEquals("JA", DxCarI18n.tr("繁", "简", "EN", "JA"))
    }

    @Test
    fun `toSimplifiedChinese converts car launcher terminology cleanly`() {
        val simp = DxCarI18n.toSimplifiedChinese("車機啟動器設定：藍牙連上時強制重啟無線網路與權限")
        assertEquals("车机启动器设置：蓝牙连上时强制重启无线网络与权限", simp)
    }
}
