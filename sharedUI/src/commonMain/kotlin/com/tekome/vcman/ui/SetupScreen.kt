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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tekome.vcman.data.SampleRubric
import com.tekome.vcman.data.SampleRubrics
import org.jetbrains.compose.resources.stringResource
import vcman.sharedui.generated.resources.Res
import vcman.sharedui.generated.resources.setup_analyze
import vcman.sharedui.generated.resources.setup_load_sample
import vcman.sharedui.generated.resources.setup_open_settings
import vcman.sharedui.generated.resources.setup_rubric_text_label
import vcman.sharedui.generated.resources.setup_rubric_title_label
import vcman.sharedui.generated.resources.setup_subject_label

data class SetupInput(
    val rubricTitle: String = "",
    val rubricText: String = "",
    val subject: String = "",
)

internal object SetupScreenTags {
    const val RUBRIC_TITLE = "setup_rubric_title"
    const val RUBRIC_TEXT = "setup_rubric_text"
    const val LOAD_SAMPLE = "setup_load_sample"
    const val SUBJECT = "setup_subject"
    const val OPEN_SETTINGS = "setup_open_settings"
    const val ANALYZE = "setup_analyze"
    const val LOADING = "setup_loading"
    const val ERROR = "setup_error"
    const val CLARIFICATION = "setup_clarification"
    const val CLARIFICATION_DETAIL = "setup_clarification_detail"
}

@Composable
internal fun SetupScreen(
    modifier: Modifier = Modifier,
    input: SetupInput,
    onInputChange: (SetupInput) -> Unit,
    loading: Boolean,
    notice: SetupNotice?,
    onAnalyze: (rubricTitle: String, rubricText: String, subject: String) -> Unit,
    onOpenSettings: () -> Unit,
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
        TextButton(
            onClick = onOpenSettings,
            modifier = Modifier.align(Alignment.End).testTag(SetupScreenTags.OPEN_SETTINGS),
        ) {
            Text(stringResource(Res.string.setup_open_settings))
        }
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
        notice?.let { SetupNoticeBlock(it) }
        Button(
            onClick = {
                onAnalyze(
                    input.rubricTitle,
                    input.rubricText,
                    input.subject,
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
private fun SetupNoticeBlock(notice: SetupNotice) {
    when (notice) {
        is SetupNotice.Error -> ErrorNotice(notice)
        is SetupNotice.Clarification -> ClarificationNotice(notice)
    }
}

@Composable
private fun ErrorNotice(notice: SetupNotice.Error) {
    Text(
        text = notice.message,
        color = MaterialTheme.colorScheme.error,
        modifier =
            Modifier
                .testTag(SetupScreenTags.ERROR)
                .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun ClarificationNotice(notice: SetupNotice.Clarification) {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(SetupScreenTags.CLARIFICATION)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = notice.title, style = MaterialTheme.typography.titleMedium)
            Text(text = notice.hint)
            notice.detail?.let { Text(text = it, modifier = Modifier.testTag(SetupScreenTags.CLARIFICATION_DETAIL)) }
        }
    }
}

@Composable
@Preview
private fun SetupScreenClarificationPreview() {
    MaterialTheme {
        SetupScreen(
            input = SetupInput(subject = "HYPE"),
            onInputChange = {},
            loading = false,
            notice =
                SetupNotice.Clarification(
                    title = "Please clarify the subject",
                    hint = "Add details such as the chain, exchange or website, then analyze again.",
                    detail = "Candidates: HYPE (Hyperliquid), HYPE (another token)",
                ),
            onAnalyze = { _, _, _ -> },
            onOpenSettings = {},
            sampleRubric = SampleRubric(title = "Demo", text = "Tieu chi 1\nTieu chi 2"),
        )
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
            notice = SetupNotice.Error("Vi du thong bao loi"),
            onAnalyze = { _, _, _ -> },
            onOpenSettings = {},
            sampleRubric = SampleRubric(title = "Demo", text = "Tieu chi 1\nTieu chi 2"),
        )
    }
}
