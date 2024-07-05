package com.skyd.anivu.ui.fragment.read

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.divider.MaterialDividerItemDecoration
import com.skyd.anivu.R
import com.skyd.anivu.base.BaseBottomSheetDialogFragment
import com.skyd.anivu.databinding.BottomSheetEnclosureBinding
import com.skyd.anivu.model.bean.EnclosureBean
import com.skyd.anivu.model.bean.LinkEnclosureBean
import com.skyd.anivu.model.worker.download.DownloadTorrentWorker
import com.skyd.anivu.ui.adapter.variety.AniSpanSize
import com.skyd.anivu.ui.adapter.variety.VarietyAdapter
import com.skyd.anivu.ui.adapter.variety.proxy.Enclosure1Proxy
import com.skyd.anivu.ui.adapter.variety.proxy.LinkEnclosure1Proxy

class EnclosureBottomSheet : BaseBottomSheetDialogFragment<BottomSheetEnclosureBinding>() {
    companion object {
        const val TAG = "ModalBottomSheet"
    }

    private val onDownload: (Any) -> Unit = {
        val url = when (it) {
            is EnclosureBean -> it.url
            is LinkEnclosureBean -> it.link
            else -> null
        }
        if (!url.isNullOrBlank()) {
            DownloadTorrentWorker.startWorker(
                context = requireContext(), torrentLink = url
            )
        }
    }

    private val adapter = VarietyAdapter(
        mutableListOf(
            Enclosure1Proxy(onDownload = onDownload),
            LinkEnclosure1Proxy(onDownload = onDownload),
        )
    )

    fun updateData(dataList: List<Any>) {
        adapter.dataList = dataList
    }

    override fun BottomSheetEnclosureBinding.initView() {
        rvBottomSheetEnclosure.layoutManager = GridLayoutManager(
            requireContext(),
            AniSpanSize.MAX_SPAN_SIZE
        ).apply {
            spanSizeLookup = AniSpanSize(adapter)
        }

        val divider = MaterialDividerItemDecoration(requireContext(), LinearLayoutManager.VERTICAL)
        divider.isLastItemDecorated = false
        divider.setDividerInsetStartResource(requireContext(), R.dimen.divider_inset_start)
        divider.setDividerInsetEndResource(requireContext(), R.dimen.divider_inset_end)
        rvBottomSheetEnclosure.addItemDecoration(divider)
        rvBottomSheetEnclosure.adapter = adapter
    }

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?) =
        BottomSheetEnclosureBinding.inflate(inflater, container, false)
}