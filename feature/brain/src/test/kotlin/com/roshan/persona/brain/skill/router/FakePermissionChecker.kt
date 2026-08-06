// Copyright (c) 2026 Roshan. All rights reserved.
// Proprietary license — see LICENSE file for details.

package com.roshan.persona.brain.skill.router

/**
 * Test-only fake [PermissionChecker] — all permissions granted by default.
 *
 * Removed from `src/main` (Phase 1 stub-removal): production code uses
 * :feature:security's [com.roshan.persona.security.integrity.RealPermissionChecker]
 * which wraps `ContextCompat.checkSelfPermission`. This fake lives in the
 * test source set so unit tests can inject predetermined grants.
 */
class FakePermissionChecker(
    private val grantedPermissions: Set<String> = emptySet(),
) : PermissionChecker {

    private val dynamicGrants = mutableSetOf<String>()

    override fun isGranted(permission: String): Boolean {
        return permission in grantedPermissions || permission in dynamicGrants
    }

    /** Grant a permission dynamically (for testing permission flow). */
    fun grant(permission: String) {
        dynamicGrants.add(permission)
    }

    /** Revoke a permission (for testing). */
    fun revoke(permission: String) {
        dynamicGrants.remove(permission)
    }

    /** Grant all permissions in a set. */
    fun grantAll(permissions: Set<String>) {
        dynamicGrants.addAll(permissions)
    }
}
