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
import org.jetbrains.compose.resources.stringResource
import vcman.sharedui.generated.resources.Res
import vcman.sharedui.generated.resources.setup_analyze
import vcman.sharedui.generated.resources.setup_api_key_label
import vcman.sharedui.generated.resources.setup_load_sample
import vcman.sharedui.generated.resources.setup_rubric_text_label
import vcman.sharedui.generated.resources.setup_rubric_title_label
import vcman.sharedui.generated.resources.setup_subject_label

data class SetupInput(
    val rubricTitle: String = "",
    val rubricText: String = "",
    val subject: String = "",
    val apiKey: String = "",
) {
    override fun toString(): String = "SetupInput(rubricTitle=$rubricTitle, rubricText=$rubricText, subject=$subject, apiKey=***)"
}

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

@Composable
fun SetupScreen(
    modifier: Modifier = Modifier,
    input: SetupInput,
    onInputChange: (SetupInput) -> Unit,
    loading: Boolean,
    error: String?,
    onAnalyze: (rubricTitle: String, rubricText: String, subject: String, apiKey: String) -> Unit,
    sampleRubric: SampleRubric = SampleRubrics.cryptoBenchScore,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = input.rubricTitle,
            onValueChange = { onInputChange(input.copy(rubricTitle = it)) },
            label = { Text(stringResource(Res.string.setup_rubric_title_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag(SetupScreenTags.RUBRIC_TITLE),
        )
        OutlinedTextField(
            value = input.rubricText,
            onValueChange = { onInputChange(input.copy(rubricText = it)) },
            label = { Text(stringResource(Res.string.setup_rubric_text_label)) },
            minLines = 6,
            maxLines = 12,
            modifier = Modifier.fillMaxWidth().testTag(SetupScreenTags.RUBRIC_TEXT),
        )
        OutlinedButton(
            onClick = {
                onInputChange(
                    input.copy(
                        rubricTitle = sampleRubric.title,
                        rubricText = sampleRubric.text,
                    ),
                )
            },
            modifier = Modifier.testTag(SetupScreenTags.LOAD_SAMPLE),
        ) {
            Text(stringResource(Res.string.setup_load_sample))
        }
        OutlinedTextField(
            value = input.subject,
            onValueChange = { onInputChange(input.copy(subject = it)) },
            label = { Text(stringResource(Res.string.setup_subject_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag(SetupScreenTags.SUBJECT),
        )
        OutlinedTextField(
            value = input.apiKey,
            onValueChange = { onInputChange(input.copy(apiKey = it)) },
            label = { Text(stringResource(Res.string.setup_api_key_label)) },
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
            onClick = {
                onAnalyze(
                    input.rubricTitle,
                    input.rubricText,
                    input.subject,
                    input.apiKey,
                )
            },
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
            }
            Text(stringResource(Res.string.setup_analyze))
        }
    }
}

@Composable
@Preview
private fun SetupScreenPreview() {
    MaterialTheme {
        SetupScreen(
            input = SetupInput(subject = "Bitcoin"),
            onInputChange = {},
            loading = false,
            error = "Vi du thong bao loi",
            onAnalyze = { _, _, _, _ -> },
            sampleRubric = SampleRubric(title = "Demo", text = "Tieu chi 1\nTieu chi 2"),
        )
    }
}
