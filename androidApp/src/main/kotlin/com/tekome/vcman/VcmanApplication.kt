package com.tekome.vcman

import android.app.Application
import com.tekome.vcman.di.AppGraph
import com.tekome.vcman.di.createAppGraph

class VcmanApplication : Application() {
    val appGraph: AppGraph by lazy { createAppGraph(this) }
}
