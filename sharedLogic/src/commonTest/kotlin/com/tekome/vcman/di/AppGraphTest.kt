package com.tekome.vcman.di

import androidx.lifecycle.viewmodel.CreationExtras
import com.tekome.vcman.data.FakeSettingsRepository
import com.tekome.vcman.data.SettingsRepository
import com.tekome.vcman.presentation.ScoreAnalysisViewModel
import com.tekome.vcman.presentation.SettingsViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraph
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

@DependencyGraph(AppScope::class)
internal interface TestAppGraph : AppGraph {
    @Provides
    @SingleIn(AppScope::class)
    fun provideSettingsRepository(): SettingsRepository = FakeSettingsRepository()
}

class AppGraphTest {
    private val graph = createGraph<TestAppGraph>()

    @Test
    fun viewModelProviders_coverEveryScreenViewModel() {
        val expected: Set<KClass<*>> = setOf(ScoreAnalysisViewModel::class, SettingsViewModel::class)

        assertEquals(expected, graph.viewModelProviders.keys)
    }

    @Test
    fun factory_createsEveryRegisteredViewModel() {
        graph.viewModelProviders.keys.forEach { type ->
            val viewModel = graph.metroViewModelFactory.create(type, CreationExtras.Empty)

            assertEquals(type, viewModel::class)
        }
    }

    @Test
    fun factory_isSingleton() {
        assertSame(graph.metroViewModelFactory, graph.metroViewModelFactory)
    }

    @Test
    fun factory_createsNewInstanceEachTime() {
        val first = graph.metroViewModelFactory.create(SettingsViewModel::class, CreationExtras.Empty)
        val second = graph.metroViewModelFactory.create(SettingsViewModel::class, CreationExtras.Empty)

        assertNotSame(first, second)
    }
}
