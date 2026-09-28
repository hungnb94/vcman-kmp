package com.tekome.vcman.ui

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `@RunWith` is `@Inherited`, so any `SetupScreenTest : ComposeUiTestRunner()` subclass is picked
 * up by JUnit4 as a Robolectric test without repeating this annotation per test class. `sdk = 34`
 * pins Robolectric to a shadow set it ships with; the project's `compileSdk`/`targetSdk` (37) is
 * newer than any Robolectric release currently supports.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
actual abstract class ComposeUiTestRunner
