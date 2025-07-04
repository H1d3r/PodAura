package com.skyd.podaura.ui.screen.filepicker

import com.skyd.mvi.AbstractMviViewModel
import com.skyd.podaura.ext.catchMap
import com.skyd.podaura.ext.startWith
import com.skyd.podaura.model.repository.FilePickerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.scan

class FilePickerViewModel(
    private val filePickerRepo: FilePickerRepository
) : AbstractMviViewModel<FilePickerIntent, FilePickerState, FilePickerEvent>() {

    override val viewState: StateFlow<FilePickerState>

    init {
        val initialVS = FilePickerState.initial()

        viewState = intentFlow
            .toFilePickerPartialStateChangeFlow()
            .debugLog("FilePickerPartialStateChange")
            .sendSingleEvent()
            .scan(initialVS) { vs, change -> change.reduce(vs) }
            .debugLog("ViewState")
            .toState(initialVS)
    }

    private fun Flow<FilePickerPartialStateChange>.sendSingleEvent(): Flow<FilePickerPartialStateChange> {
        return onEach { change ->
            val event = when (change) {
                is FilePickerPartialStateChange.FileListResult.Failed ->
                    FilePickerEvent.FileListResultEvent.Failed(change.msg)

                else -> return@onEach
            }
            sendEvent(event)
        }
    }

    private fun Flow<FilePickerIntent>.toFilePickerPartialStateChangeFlow(): Flow<FilePickerPartialStateChange> {
        return merge(
            merge(
                filterIsInstance<FilePickerIntent.NewLocation>()
                    .map { it.path to it.extensionName },
                filterIsInstance<FilePickerIntent.Refresh>()
                    .map { it.path to it.extensionName },
            ).flatMapConcat { data ->
                val path = data.first
                val extensionName = data.second
                filePickerRepo.requestFiles(
                    path = path,
                    extensionName = extensionName,
                ).map {
                    FilePickerPartialStateChange.FileListResult.Success(path = path, list = it)
                }.startWith(FilePickerPartialStateChange.FileListResult.Loading).catchMap {
                    FilePickerPartialStateChange.FileListResult.Failed(
                        path = path,
                        msg = it.message.toString(),
                    )
                }
            }
        )
    }
}