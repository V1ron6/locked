package com.locked.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * Watches which app comes to the foreground. During a focus phase, if that app is locked,
 * the block screen is launched on top of it. Window content is never read.
 */
class LockAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: LockAccessibilityService? = null

        private val IGNORED = setOf("com.android.systemui")
        private val SETTINGS_AND_INSTALLERS = setOf(
            "com.android.settings",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.samsung.android.packageinstaller",
        )
    }

    private var lastPkg: String? = null

    override fun onServiceConnected() {
        instance = this
        Engine.tick(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg in IGNORED) return
        lastPkg = pkg
        if (pkg == packageName) return
        check(pkg)
    }

    /** Called by the engine at every boundary so an app that was already open gets blocked too. */
    fun onTick() {
        val pkg = lastPkg ?: return
        if (pkg == packageName) return
        check(pkg)
    }

    private fun check(pkg: String) {
        val st = Engine.state(this)
        if (!st.active || !st.locked) return
        val allowed = Store.allowedEffective(this)
        val tampering = Store.tamperBlock(this) && pkg in SETTINGS_AND_INSTALLERS && pkg !in allowed
        if (pkg in st.packages || tampering) showBlock(pkg)
    }

    private fun showBlock(pkg: String) {
        startActivity(
            Intent(this, BlockActivity::class.java)
                .putExtra("pkg", pkg)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
        )
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }
}
