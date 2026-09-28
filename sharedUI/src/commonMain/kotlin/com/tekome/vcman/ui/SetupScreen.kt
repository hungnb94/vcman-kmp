package com.tekome.vcman.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.password
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tekome.vcman.data.SampleRubric
import com.tekome.vcman.data.SampleRubrics

/** Stable node identifiers for [SetupScreen], so tests do not depend on user-visible labels. */
internal object SetupScreenTags {
    const val RUBRIC_TITLE = "setup_rubric_title"
    const val RUBRIC_TEXT = "setup_rubric_text"
    const val LOAD_SAMPLE = "setup_load_sample"
    const val SUBJECT = "setup_subject"
    const val API_KEY = "setup_api_key"
    const val ANALYZE = "setup_analyze"
    const val LOADING = "setup_loading"
    const val ERROR = "setup_error"
}

/**
 * Screen 1/2 of the POC: rubric setup, subject, and API key in one screen.
 *
 * Stateless except for the local text-field state below: [loading] and [error] are read directly
 * from the caller's props (see `AnalysisUiState` in `sharedLogic`) and are never copied into
 * local state, so there is no stale ("ghost") error left over from a previous request. Tapping
 * "Phan tich" simply forwards the current field values through [onAnalyze] - this composable does
 * not call any service/ViewModel and does not apply business validation itself (that lives in
 * `ScoreAnalysisViewModel.analyze`).
 *
 * The four fields intentionally use `remember`, not `rememberSaveable`: `rememberSaveable` writes
 * into the saved-instance-state `Bundle`, which the OS may persist to disk for process-death
 * recovery. For [apiKey] that would violate the "session-only, no persistence" requirement; for
 * consistency the other three fields follow the same rule (and `rubricText` can be long enough to
 * risk `TransactionTooLargeException` if it were ever saved). The trade-off is that field content
 * is lost on an Android configuration change (e.g. rotation) - acceptable for this iOS-focused POC.
 *
 * @param onAnalyze called with the current `(rubricTitle, rubricText, subject, apiKey)` values,
 *   in that order, exactly once per tap while not [loading]. Matches the signature of
 *   `ScoreAnalysisViewModel::analyze`, so a caller can pass that method reference directly.
 * @param sampleRubric filled into the rubric fields by "Load rubric mau"; defaults to the built-in
 *   [SampleRubrics.cryptoBenchScore]. Injectable so tests/previews do not depend on that data.
 */
@Composable
fun SetupScreen(
    modifier: Modifier = Modifier,
    loading: Boolean,
    error: String?,
    onAnalyze: (rubricTitle: String, rubricText: String, subject: String, apiKey: String) -> Unit,
    sampleRubric: SampleRubric = SampleRubrics.cryptoBenchScore,
) {
    var rubricTitle by remember { mutableStateOf("") }
    var rubricText by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") } // remember only - see KDoc above, never log this.

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = rubricTitle,
            onValueChange = { rubricTitle = it },
            label = { Text("Tieu de rubric") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag(SetupScreenTags.RUBRIC_TITLE),
        )
        OutlinedTextField(
            value = rubricText,
            onValueChange = { rubricText = it },
            label = { Text("Noi dung rubric") },
            minLines = 6,
            maxLines = 12,
            modifier = Modifier.fillMaxWidth().testTag(SetupScreenTags.RUBRIC_TEXT),
        )
        OutlinedButton(
            onClick = {
                rubricTitle = sampleRubric.title
                rubricText = sampleRubric.text
            },
            modifier = Modifier.testTag(SetupScreenTags.LOAD_SAMPLE),
        ) {
            Text("Load rubric mau")
        }
        OutlinedTextField(
            value = subject,
            onValueChange = { subject = it },
            label = { Text("Project / company / coin") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag(SetupScreenTags.SUBJECT),
        )
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("API key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    autoCorrectEnabled = false,
                ),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .semantics { password() }
                    .testTag(SetupScreenTags.API_KEY),
        )
        if (error != null) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                modifier =
                    Modifier
                        .testTag(SetupScreenTags.ERROR)
                        .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Button(
            onClick = { onAnalyze(rubricTitle, rubricText, subject, apiKey) },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth().testTag(SetupScreenTags.ANALYZE),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier =
                        Modifier
                            .size(18.dp)
                            .testTag(SetupScreenTags.LOADING),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
                Text("Phan tich")
            } else {
                Text("Phan tich")
            }
        }
    }
}

@Composable
@Preview
private fun SetupScreenPreview() {
    MaterialTheme {
        SetupScreen(
            loading = false,
            error = "Vi du thong bao loi",
            onAnalyze = { _, _, _, _ -> },
            sampleRubric = SampleRubric(title = "Demo", text = "Tieu chi 1\nTieu chi 2"),
        )
    }
}
