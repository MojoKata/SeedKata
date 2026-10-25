package com.cobaltiron.diceseed

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.random.Random

/**
 * Walks the real app UI and records one PNG per screen/state into
 * screenshots/droid/ at the repo root.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-440dpi")
class ScreenshotWalk {

    @get:Rule
    val rule = createComposeRule()

    private fun shot(name: String) {
        rule.onRoot().captureRoboImage("../../screenshots/droid/$name.png")
    }

    /** Full-window capture for states with a dialog (second compose root). */
    @OptIn(ExperimentalRoborazziApi::class)
    private fun screenShot(name: String) {
        captureScreenRoboImage("../../screenshots/droid/$name.png")
    }

    @Test
    fun walkAllScreens() {
        Bip39.load(ApplicationProvider.getApplicationContext())
        val vm = AppViewModel()
        var backDispatcher: OnBackPressedDispatcher? = null
        rule.setContent {
            backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
            DiceSeedApp(vm)
        }

        // Screen 1: Launch
        shot("screen1")
        rule.onNodeWithText("Get started").performClick()

        // Screen 2: Seed Length
        shot("screen2")
        rule.onNodeWithText("12 words").performClick()

        // Screen 3: Your Dice
        shot("screen3")
        rule.onNodeWithText("6-sided").performClick()

        // Screen 4: Dice Quality
        shot("screen4")
        rule.onNodeWithText("I trust them (1x)").performClick()

        // Screen 5: Rolling — ~40 random d6 rolls for a lively mid-state
        val rng = Random(20260923)
        repeat(40) { rule.onNodeWithText("${rng.nextInt(1, 7)}").performClick() }
        rule.waitForIdle()
        shot("screen5-6sided")

        // Cancel confirmation (req 2.8): a real system back press with rolls
        // at stake must open the dialog, not navigate away
        rule.runOnUiThread { backDispatcher!!.onBackPressed() }
        rule.waitForIdle()
        check(vm.cancelDialogOpen) { "back press must open the cancel dialog" }
        rule.onNodeWithText("Cancel dice rolling?").assertExists()
        screenShot("screen5-cancel")
        rule.onNodeWithText("Keep rolling").performClick()

        // Undo one roll (req 2.7), then finish the session
        rule.onNodeWithContentDescription("Undo last roll").performClick()
        while (!vm.rollingComplete) {
            rule.onNodeWithText("${rng.nextInt(1, 7)}").performClick()
        }
        rule.waitForIdle()
        shot("screen5-complete")
        rule.onNodeWithText("Continue").performClick()

        // Screen 6: Words (12)
        shot("screen6-12words")
        rule.onNodeWithText("Verify").performClick()

        // Screen 7: Verify
        rule.waitForIdle()
        shot("screen7")

        // Complete verification deterministically: always tap the word the
        // backup would show for the asked position
        var guard = 0
        while (!vm.verifyComplete && guard < 50) {
            val correct = vm.words[vm.verifyWordNumber - 1]
            rule.onNodeWithText(correct).performClick()
            guard++
        }
        rule.waitForIdle()
        shot("screen7-complete")
        rule.onNodeWithText("Done").performClick()
        rule.waitForIdle()
        shot("screen1-after-done")
    }

    @Test
    fun walk24WordsWithPaging() {
        Bip39.load(ApplicationProvider.getApplicationContext())
        val vm = AppViewModel()
        rule.setContent { DiceSeedApp(vm) }

        rule.onNodeWithText("Get started").performClick()
        rule.onNodeWithText("24 words").performClick()
        rule.onNodeWithText("8-sided").performClick()
        rule.onNodeWithText("I trust them (1x)").performClick()

        // Mid-session d8 shot, then roll to completion
        val rng = Random(20260924)
        repeat(40) { rule.onNodeWithText("${rng.nextInt(1, 9)}").performClick() }
        rule.waitForIdle()
        shot("screen5-8sided")
        while (!vm.rollingComplete) {
            rule.onNodeWithText("${rng.nextInt(1, 9)}").performClick()
        }
        rule.onNodeWithText("Continue").performClick()
        rule.waitForIdle()

        // Words screen: both pages of the 24-word list (req 3.2)
        shot("screen6-24words")
        rule.onNodeWithContentDescription("Next page").performClick()
        rule.waitForIdle()
        shot("screen6-24words-page2")
        rule.onNodeWithText("Trust me!").performClick()
        rule.waitForIdle()
    }
}
