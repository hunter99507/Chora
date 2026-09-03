package com.craftworks.music.managers

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object DataRefreshManager {
    private val _dataSourceChangedEvent = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 16)
    val dataSourceChangedEvent = _dataSourceChangedEvent.asSharedFlow()

    fun notifyDataSourcesChanged() {
        _dataSourceChangedEvent.tryEmit(Unit)
    }
}