package com.tekome.vcman.presentation

import com.tekome.vcman.data.ApiKey
import com.tekome.vcman.data.ConnectionTestResult
import com.tekome.vcman.data.WebSearchKeyTester
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Runs one key test at a time in the caller's [scope]; [reset] drops a result that no longer matches the key. */
internal class KeyCheck(
    private val scope: CoroutineScope,
    private val tester: WebSearchKeyTester,
    private val onStateChange: (ConnectionTestState) -> Unit,
) {
    private var job: Job? = null

    fun run(key: ApiKey) {
        if (key.value.isBlank() || job?.isActive == true) return
        onStateChange(ConnectionTestState.Testing)
        job =
            scope.launch {
                val result =
                    try {
                        tester.test(key)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        ConnectionTestResult.Unexpected
                    }
                onStateChange(ConnectionTestState.Done(result))
            }
    }

    fun reset() {
        job?.cancel()
        job = null
        onStateChange(ConnectionTestState.Idle)
    }
}
