package dev.orbit.simlab.presentation

import android.content.pm.PackageManager
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.orbit.simlab.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkflowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun offlineCreateEditAndValidationFlow() {
        assertEquals(PackageManager.PERMISSION_DENIED,
            compose.activity.packageManager.checkPermission("android.permission.INTERNET", compose.activity.packageName))
        compose.onNodeWithText("پروژهٔ جدید").performClick()
        compose.onNodeWithText("نام").performTextInput("پروژهٔ آزمایشی")
        compose.onNodeWithText("ساخت").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("ویرایش نمودار و قوانین").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("ویرایش نمودار و قوانین").performClick()
        compose.onNodeWithText("+ SOURCE").performClick()
        compose.onNodeWithText("+ SINK").performClick()
        compose.onNodeWithText("بررسی نمودار").performClick()
    }

    @Test fun builtInScenarioRunsAndShowsResults() {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("شبیه‌ساز صف اورژانس").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("شبیه‌ساز صف اورژانس").performClick()
        compose.onNodeWithText("اجرای آزمایش").performClick()
        compose.onNodeWithText("ادامه / اجرا").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("نمایش نتیجه").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("نمایش نتیجه").performClick()
        compose.onNodeWithText("تولیدشده").assertExists()
        compose.onNodeWithText("تکمیل‌شده").assertExists()
    }
}
