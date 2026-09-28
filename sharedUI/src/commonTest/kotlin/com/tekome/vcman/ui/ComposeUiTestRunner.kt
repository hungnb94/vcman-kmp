package com.tekome.vcman.ui

/**
 * Empty per-target base class for Compose UI tests (`runComposeUiTest`). On the Android host test
 * target it wires up `@RunWith(RobolectricTestRunner::class)` (see the `androidHostTest` actual):
 * JUnit4's `@RunWith` is `@Inherited`, so a subclass of this base class picks up that runner
 * without any platform-specific code appearing in this shared file. On iOS the actual is a no-op
 * because `runComposeUiTest` already runs on the real platform there - no Robolectric needed.
 */
expect abstract class ComposeUiTestRunner()
