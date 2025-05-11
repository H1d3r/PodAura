package com.skyd.podaura.ui.screen.download

import com.skyd.podaura.model.bean.download.DownloadInfoBean
import com.skyd.podaura.model.bean.download.bt.BtDownloadInfoBean


internal sealed interface DownloadPartialStateChange {
    fun reduce(oldState: DownloadState): DownloadState


    sealed interface LoadingDialog : DownloadPartialStateChange {
        data object Show : LoadingDialog {
            override fun reduce(oldState: DownloadState) = oldState.copy(loadingDialog = true)
        }
    }

    sealed interface DownloadListResult : DownloadPartialStateChange {
        override fun reduce(oldState: DownloadState): DownloadState {
            return when (this) {
                is Success -> oldState.copy(
                    downloadListState = DownloadListState.Success(
                        downloadInfoBeanList = downloadInfoBeanList,
                        btDownloadInfoBeanList = btDownloadInfoBeanList,
                    ),
                    loadingDialog = false,
                )

                is Failed -> oldState.copy(
                    downloadListState = DownloadListState.Failed(msg = msg),
                    loadingDialog = false,
                )

                Loading -> oldState.copy(
                    downloadListState = DownloadListState.Loading,
                    loadingDialog = false,
                )
            }
        }

        data class Success(
            val downloadInfoBeanList: List<DownloadInfoBean>,
            val btDownloadInfoBeanList: List<BtDownloadInfoBean>,
        ) : DownloadListResult

        data class Failed(val msg: String) : DownloadListResult
        data object Loading : DownloadListResult
    }
}
