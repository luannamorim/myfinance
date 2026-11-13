package io.github.luannamorim.myfinance.ui.feature.backup

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.luannamorim.myfinance.core.time.FakeTimeSource
import io.github.luannamorim.myfinance.data.backup.KeyState
import io.github.luannamorim.myfinance.data.prefs.BackupFailure
import io.github.luannamorim.myfinance.data.prefs.BackupState
import io.github.luannamorim.myfinance.domain.backup.BackupFileInfo
import io.github.luannamorim.myfinance.testutil.PHONE_TALL
import io.github.luannamorim.myfinance.testutil.snapBothThemes
import io.github.luannamorim.myfinance.ui.theme.FinanceTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.ZoneId

/**
 * Interactions run with Robolectric's default qualifiers: with explicit xxhdpi size qualifiers an AlertDialog that
 * holds a text field never becomes idle under Robolectric (the screenshot has no dialog and uses the tall phone).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class BackupScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    /** Local wall time (the screen shows dates in the device zone). */
    private fun ms(date: String, time: String) = FakeTimeSource.at(date, time, ZoneId.systemDefault())

    private fun file(date: String, time: String, size: Long) =
        BackupFileInfo("content://backup/$date-$time", "myfinance-backup-${date.replace("-", "")}-${time.replace(":", "")}00.myfinance", size, ms(date, time))

    private val configured = BackupUiState(
        loading = false,
        keyState = KeyState.READY,
        backup = BackupState(
            folderUri = "content://tree/backups",
            folderName = "MyFinance",
            lastSuccessMs = ms("2026-10-30", "21:05"),
            lastError = BackupFailure.FOLDER_UNAVAILABLE,
        ),
        files = listOf(
            file("2026-10-30", "21:05", 2_480_000),
            file("2026-10-29", "08:12", 2_475_000),
            file("2026-10-28", "19:40", 184_320),
        ),
    )

    @Test
    @Config(qualifiers = PHONE_TALL)
    fun backup() = compose.snapBothThemes("backup") {
        BackupContent(configured, BackupActions())
    }

    @Test
    fun notConfigured_explainsWhatIsMissing_andDisablesBackUpNow() {
        compose.setContent { FinanceTheme { BackupContent(BackupUiState(loading = false), BackupActions()) } }
        compose.onNodeWithText("Set password").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("No folder chosen").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Never").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Back up now").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Set a password and choose a folder to start backing up.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun restore_confirmDialog_showsDate_andConfirmsWithoutPassword() {
        var confirmed: CharArray? = charArrayOf('x')
        val pending = PendingRestore("content://backup/1", 1_000, ms("2026-10-30", "21:05"), needsPassword = false)
        compose.setContent {
            FinanceTheme { BackupContent(configured.copy(pending = pending), BackupActions(onConfirmRestore = { confirmed = it })) }
        }
        compose.onNodeWithText("Restore backup?").assertIsDisplayed()
        compose.onNodeWithText("Backup from 30/10/2026 at 21:05", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Restore").performClick()
        assertNull(confirmed)
    }

    @Test
    fun restore_withAnotherPassword_asksForIt() {
        var submitted: String? = null
        val pending = PendingRestore("content://backup/1", 1_000, ms("2026-10-30", "21:05"), needsPassword = true, passwordError = true)
        compose.setContent {
            FinanceTheme { BackupContent(configured.copy(pending = pending), BackupActions(onConfirmRestore = { submitted = it?.concatToString() })) }
        }
        compose.onNodeWithText("Wrong password").assertIsDisplayed()
        compose.onNode(hasSetTextAction() and hasText("Password")).performTextInput("minha senha")
        compose.onNodeWithText("Restore").performClick()
        assertEquals("minha senha", submitted)
    }

    @Test
    fun setPassword_validatesLengthAndConfirmation() {
        var saved: String? = null
        compose.setContent {
            FinanceTheme { BackupContent(BackupUiState(loading = false), BackupActions(onSetPassword = { saved = it.concatToString() })) }
        }
        compose.onNodeWithText("Set password").performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText("Password")).performTextInput("curta")
        compose.onNode(hasSetTextAction() and hasText("Confirm password")).performTextInput("curta")
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Use at least 8 characters").assertIsDisplayed()
        assertNull(saved)

        compose.onNode(hasSetTextAction() and hasText("Password")).performTextInput("-mais-longa")
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Passwords do not match").assertIsDisplayed()
        assertNull(saved)

        compose.onNode(hasSetTextAction() and hasText("Confirm password")).performTextInput("-mais-longa")
        compose.onNodeWithText("Save").performClick()
        assertEquals("curta-mais-longa", saved)
    }

    @Test
    @Config(qualifiers = "pt-rBR-$PHONE_TALL")
    fun portuguese() {
        compose.setContent { FinanceTheme { BackupContent(configured, BackupActions()) } }
        compose.onNodeWithText("Alterar senha").assertIsDisplayed()
        compose.onNodeWithText("Fazer backup agora").assertIsDisplayed()
        compose.onNodeWithText("Não foi possível gravar na pasta de backup. Escolha a pasta de novo.").assertIsDisplayed()
    }
}
