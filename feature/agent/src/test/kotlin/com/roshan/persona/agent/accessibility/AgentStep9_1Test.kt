// Copyright (c) 2026 Roshan. All rights reserved.
// Proprietary license — see LICENSE file for details.

package com.roshan.persona.agent.accessibility

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.agent.automation.AutomationAvailabilityReport
import com.roshan.persona.agent.automation.AutomationMethod
import com.roshan.persona.agent.permission.AccessibilityPermissionState
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * NOUS — Module 9 Step 9.1 Tests (NousAccessibilityService + Registry +
 * PermissionManager + MethodDetector + InterfaceImpl).
 *
 * 18 tests covering:
 *  - AccessibilityServiceRegistry — register/unregister/getInstance/isActive — 5 tests
 *  - AutomationMethod enum — properties — 3 tests
 *  - AccessibilityPermissionState enum — properties — 2 tests
 *  - AutomationAvailabilityReport — computed properties — 3 tests
 *  - AccessibilityEventWrapper — computed properties — 3 tests
 *  - NousAccessibilityServiceInterfaceImpl — delegation logic — 2 tests
 */
class AgentStep9_1Test {

    // ═══════════════════════════════════════════════════════════════════════════
    // AccessibilityServiceRegistry (5 tests)
    // ═══════════════════════════════════════════════════════════════════════════

    @Before
    fun setup() {
        AccessibilityServiceRegistry.unregister()  // Clean state
    }

    @After
    fun teardown() {
        AccessibilityServiceRegistry.unregister()  // Clean up
    }

    @Test
    fun `Registry getInstance returns null when not registered`() {
        assertThat(AccessibilityServiceRegistry.getInstance()).isNull()
        assertThat(AccessibilityServiceRegistry.isActive()).isFalse()
    }

    @Test
    fun `Registry register sets active service`() {
        val service = mockk<NousAccessibilityService>(relaxed = true)
        AccessibilityServiceRegistry.register(service)
        assertThat(AccessibilityServiceRegistry.getInstance()).isEqualTo(service)
        assertThat(AccessibilityServiceRegistry.isActive()).isTrue()
    }

    @Test
    fun `Registry unregister clears active service`() {
        val service = mockk<NousAccessibilityService>(relaxed = true)
        AccessibilityServiceRegistry.register(service)
        AccessibilityServiceRegistry.unregister()
        assertThat(AccessibilityServiceRegistry.getInstance()).isNull()
        assertThat(AccessibilityServiceRegistry.isActive()).isFalse()
    }

    @Test
    fun `Registry register replaces previous instance`() {
        val service1 = mockk<NousAccessibilityService>(relaxed = true)
        val service2 = mockk<NousAccessibilityService>(relaxed = true)
        AccessibilityServiceRegistry.register(service1)
        AccessibilityServiceRegistry.register(service2)
        assertThat(AccessibilityServiceRegistry.getInstance()).isEqualTo(service2)
    }

    @Test
    fun `Registry unregister when already null does not crash`() {
        AccessibilityServiceRegistry.unregister()  // Already null
        assertThat(AccessibilityServiceRegistry.isActive()).isFalse()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AutomationMethod enum (3 tests)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `AutomationMethod LADB is fast tier with 10ms speed`() {
        assertThat(AutomationMethod.LADB.isFastTier).isTrue()
        assertThat(AutomationMethod.LADB.isAvailable).isTrue()
        assertThat(AutomationMethod.LADB.speedMs).isEqualTo(10L)
    }

    @Test
    fun `AutomationMethod ACCESSIBILITY is not fast tier with 500ms speed`() {
        assertThat(AutomationMethod.ACCESSIBILITY.isFastTier).isFalse()
        assertThat(AutomationMethod.ACCESSIBILITY.isAvailable).isTrue()
        assertThat(AutomationMethod.ACCESSIBILITY.speedMs).isEqualTo(500L)
    }

    @Test
    fun `AutomationMethod NONE is not available`() {
        assertThat(AutomationMethod.NONE.isAvailable).isFalse()
        assertThat(AutomationMethod.NONE.isFastTier).isFalse()
        assertThat(AutomationMethod.NONE.speedMs).isEqualTo(0L)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AccessibilityPermissionState enum (2 tests)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `AccessibilityPermissionState GRANTED has correct display name`() {
        assertThat(AccessibilityPermissionState.GRANTED.displayName).isEqualTo("Granted")
    }

    @Test
    fun `AccessibilityPermissionState NOT_GRANTED has correct display name`() {
        assertThat(AccessibilityPermissionState.NOT_GRANTED.displayName).isEqualTo("Not Granted")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AutomationAvailabilityReport (3 tests)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `AutomationAvailabilityReport ladbAvailable true when supported and enabled`() {
        val report = AutomationAvailabilityReport(
            ladbSupported = true,
            wirelessDebuggingEnabled = true,
            accessibilityEnabled = false,
            bestMethod = AutomationMethod.LADB,
            androidApiLevel = 30,
            androidVersion = "11",
        )
        assertThat(report.ladbAvailable).isTrue()
        assertThat(report.anyMethodAvailable).isTrue()
    }

    @Test
    fun `AutomationAvailabilityReport ladbAvailable false when not enabled`() {
        val report = AutomationAvailabilityReport(
            ladbSupported = true,
            wirelessDebuggingEnabled = false,
            accessibilityEnabled = true,
            bestMethod = AutomationMethod.ACCESSIBILITY,
            androidApiLevel = 30,
            androidVersion = "11",
        )
        assertThat(report.ladbAvailable).isFalse()
        assertThat(report.anyMethodAvailable).isTrue()  // Accessibility is available
    }

    @Test
    fun `AutomationAvailabilityReport anyMethodAvailable false when NONE`() {
        val report = AutomationAvailabilityReport(
            ladbSupported = false,
            wirelessDebuggingEnabled = false,
            accessibilityEnabled = false,
            bestMethod = AutomationMethod.NONE,
            androidApiLevel = 29,
            androidVersion = "10",
        )
        assertThat(report.ladbAvailable).isFalse()
        assertThat(report.anyMethodAvailable).isFalse()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AccessibilityEventWrapper (3 tests)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `AccessibilityEventWrapper isWindowStateChanged true for TYPE_WINDOW_STATE_CHANGED`() {
        val wrapper = AccessibilityEventWrapper(
            eventType = 32,  // AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            packageName = "com.whatsapp",
            className = "HomeActivity",
            text = emptyList(),
            timestampMs = 1000L,
        )
        assertThat(wrapper.isWindowStateChanged).isTrue()
        assertThat(wrapper.isViewClicked).isFalse()
        assertThat(wrapper.isViewTextChanged).isFalse()
    }

    @Test
    fun `AccessibilityEventWrapper isViewClicked true for TYPE_VIEW_CLICKED`() {
        val wrapper = AccessibilityEventWrapper(
            eventType = 1,  // AccessibilityEvent.TYPE_VIEW_CLICKED
            packageName = "com.whatsapp",
            className = "Button",
            text = listOf("Send"),
            timestampMs = 1000L,
        )
        assertThat(wrapper.isViewClicked).isTrue()
        assertThat(wrapper.isWindowStateChanged).isFalse()
    }

    @Test
    fun `AccessibilityEventWrapper isViewTextChanged true for TYPE_VIEW_TEXT_CHANGED`() {
        val wrapper = AccessibilityEventWrapper(
            eventType = 16,  // AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
            packageName = "com.whatsapp",
            className = "EditText",
            text = listOf("Hello"),
            timestampMs = 1000L,
        )
        assertThat(wrapper.isViewTextChanged).isTrue()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // NousAccessibilityServiceInterfaceImpl (2 tests)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `InterfaceImpl isServiceEnabled returns false when service not registered`() {
        AccessibilityServiceRegistry.unregister()  // Ensure not registered
        val impl = NousAccessibilityServiceInterfaceImpl()
        assertThat(impl.isServiceEnabled()).isFalse()
    }

    @Test
    fun `InterfaceImpl isServiceEnabled returns true when service registered`() {
        val service = mockk<NousAccessibilityService>(relaxed = true)
        every { service.isServiceActive() } returns true
        AccessibilityServiceRegistry.register(service)

        val impl = NousAccessibilityServiceInterfaceImpl()
        assertThat(impl.isServiceEnabled()).isTrue()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Banking App Blacklist (2 tests)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `Banking app blacklist contains Google Pay package`() {
        assertThat(NousAccessibilityService.BANKING_APP_BLACKLIST).contains("com.google.android.apps.nbu.paisa.user")
    }

    @Test
    fun `Banking app blacklist contains PhonePe and Paytm packages`() {
        assertThat(NousAccessibilityService.BANKING_APP_BLACKLIST).contains("com.phonepe.app")
        assertThat(NousAccessibilityService.BANKING_APP_BLACKLIST).contains("net.one97.paytm")
    }
}
