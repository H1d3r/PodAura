package com.skyd.podaura.ui.screen.download

import com.skyd.mvi.AbstractMviViewModel
import com.skyd.mvi.MviSingleEvent
import com.skyd.podaura.ext.catchMap
import com.skyd.podaura.ext.startWith
import com.skyd.podaura.model.repository.download.DownloadRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.take

class DownloadViewModel(
    private val downloadRepo: DownloadRepository
) : AbstractMviViewModel<DownloadIntent, DownloadState, MviSingleEvent>() {

    override val viewState: StateFlow<DownloadState>

    init {
        val initialVS = DownloadState.initial()

        viewState = merge(
            intentFlow.filterIsInstance<DownloadIntent.Init>().take(1),
            intentFlow.filterNot { it is DownloadIntent.Init }
        )
            .toReadPartialStateChangeFlow()
            .debugLog("DownloadPartialStateChange")
            .scan(initialVS) { vs, change -> change.reduce(vs) }
            .debugLog("ViewState")
            .toState(initialVS)
    }

    private fun Flow<DownloadIntent>.toReadPartialStateChangeFlow(): Flow<DownloadPartialStateChange> {
        return merge(
            filterIsInstance<DownloadIntent.Init>().flatMapConcat {
                combine(
                    downloadRepo.requestDownloadTasksList(),
                    downloadRepo.requestBtDownloadTasksList(),
                ) { downloadTasks, btDownloadTasks ->
                    DownloadPartialStateChange.DownloadListResult.Success(
                        downloadInfoBeanList = downloadTasks,
                        btDownloadInfoBeanList = btDownloadTasks,
                    )
                }.startWith(DownloadPartialStateChange.DownloadListResult.Loading)
                    .catchMap { DownloadPartialStateChange.DownloadListResult.Failed(it.message.toString()) }
            },
        )
    }
}