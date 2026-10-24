package com.kappi.crunch.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateManagerTest {

    @Test
    fun testIsNewerVersion_greaterVersions() {
        assertTrue(AppUpdateManager.isNewerVersion("1.0.3", "1.0.2"))
        assertTrue(AppUpdateManager.isNewerVersion("v1.0.3", "1.0.2"))
        assertTrue(AppUpdateManager.isNewerVersion("V1.1.0", "1.0.2"))
        assertTrue(AppUpdateManager.isNewerVersion("2.0.0", "1.0.2"))
        assertTrue(AppUpdateManager.isNewerVersion("1.0.2.1", "1.0.2"))
        assertTrue(AppUpdateManager.isNewerVersion("v1.0.3-beta", "1.0.2"))
    }

    @Test
    fun testIsNewerVersion_equalOrLesserVersions() {
        assertFalse(AppUpdateManager.isNewerVersion("1.0.2", "1.0.2"))
        assertFalse(AppUpdateManager.isNewerVersion("v1.0.2", "1.0.2"))
        assertFalse(AppUpdateManager.isNewerVersion("1.0.1", "1.0.2"))
        assertFalse(AppUpdateManager.isNewerVersion("0.9.9", "1.0.2"))
        assertFalse(AppUpdateManager.isNewerVersion("1.0.0", "1.0.2"))
    }

    @Test
    fun testFormatFileSize() {
        assertEquals("0.0 MB", AppUpdateManager.formatFileSize(0))
        assertEquals("10.0 MB", AppUpdateManager.formatFileSize(10 * 1024 * 1024))
        assertEquals("24.5 MB", AppUpdateManager.formatFileSize((24.5 * 1024 * 1024).toLong()))
    }

    @Test
    fun testSelectBestApkAsset_universalFallback() {
        val assets = listOf(
            GitHubAsset(name = "crunch-sources.zip", size = 1000, downloadUrl = "https://example.com/zip"),
            GitHubAsset(name = "app-universal-release.apk", size = 25000000, downloadUrl = "https://example.com/universal.apk"),
            GitHubAsset(name = "notes.txt", size = 50, downloadUrl = "https://example.com/notes.txt")
        )
        val selected = AppUpdateManager.selectBestApkAsset(assets)
        assertEquals("app-universal-release.apk", selected?.name)
    }

    @Test
    fun testSelectBestApkAsset_singleApk() {
        val assets = listOf(
            GitHubAsset(name = "Crunch-v1.0.3.apk", size = 20000000, downloadUrl = "https://example.com/crunch.apk")
        )
        val selected = AppUpdateManager.selectBestApkAsset(assets)
        assertEquals("Crunch-v1.0.3.apk", selected?.name)
    }
}
