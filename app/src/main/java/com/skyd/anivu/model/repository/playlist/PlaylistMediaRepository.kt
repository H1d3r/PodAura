package com.skyd.anivu.model.repository.playlist

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.skyd.anivu.appContext
import com.skyd.anivu.base.BaseRepository
import com.skyd.anivu.ext.dataStore
import com.skyd.anivu.ext.flowOf
import com.skyd.anivu.model.bean.playlist.PlaylistMediaBean
import com.skyd.anivu.model.bean.playlist.PlaylistMediaWithArticleBean
import com.skyd.anivu.model.db.dao.ArticleDao
import com.skyd.anivu.model.db.dao.EnclosureDao
import com.skyd.anivu.model.db.dao.playlist.PlaylistDao
import com.skyd.anivu.model.db.dao.playlist.PlaylistMediaDao
import com.skyd.anivu.model.db.dao.playlist.PlaylistMediaDao.Companion.ORDER_DELTA
import com.skyd.anivu.model.db.dao.playlist.PlaylistMediaDao.Companion.ORDER_MIN_DELTA
import com.skyd.anivu.model.preference.behavior.playlist.BasePlaylistSortByPreference.Companion.CREATE_TIME
import com.skyd.anivu.model.preference.behavior.playlist.BasePlaylistSortByPreference.Companion.MANUAL
import com.skyd.anivu.model.preference.behavior.playlist.PlaylistMediaSortAscPreference
import com.skyd.anivu.model.preference.behavior.playlist.PlaylistMediaSortByPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class PlaylistMediaRepository @Inject constructor(
    private val articleDao: ArticleDao,
    private val enclosureDao: EnclosureDao,
    private val playlistDao: PlaylistDao,
    private val playlistMediaDao: PlaylistMediaDao,
    private val pagingConfig: PagingConfig,
) : BaseRepository(), IPlaylistMediaRepository {
    private suspend fun PlaylistMediaWithArticleBean.updateMediaMetadata(): PlaylistMediaWithArticleBean {
        var result = this
        var realArticleId = playlistMediaBean.articleId
        if (realArticleId != null) {
            val oldArticleIdNotExists = articleDao.exists(realArticleId) == 0
            if (oldArticleIdNotExists) {
                realArticleId = enclosureDao.getMediaArticleId(playlistMediaBean.url)
                if (realArticleId != null) {
                    playlistMediaDao.updatePlaylistMediaArticleId(
                        playlistId = playlistMediaBean.playlistId,
                        url = playlistMediaBean.url,
                        articleId = realArticleId
                    )
                }
            }
            if (oldArticleIdNotExists) {
                result = copy(
                    article = realArticleId?.let { articleDao.getArticleWithFeed(it).first() },
                )
                // todo use worker to update articleId
            }
            result.playlistMediaBean.duration = article?.articleWithEnclosure?.media?.duration
            result.playlistMediaBean.thumbnail = article?.getThumbnail()
        } else {
            if (playlistMediaBean.isLocalFile) {
                playlistMediaBean.updateLocalMediaMetadata()
            }
        }
        return result
    }

    fun requestPlaylistMediaListPaging(playlistId: String): Flow<PagingData<PlaylistMediaWithArticleBean>> {
        return appContext.dataStore.flowOf(
            PlaylistMediaSortByPreference, PlaylistMediaSortAscPreference
        ).flatMapLatest { (sortBy, sortAsc) ->
            val sortByColumnName = when (sortBy) {
                CREATE_TIME -> PlaylistMediaBean.CREATE_TIME_COLUMN
                MANUAL -> PlaylistMediaBean.ORDER_POSITION_COLUMN
                else -> PlaylistMediaBean.ORDER_POSITION_COLUMN
            }
            val realSortAsc = if (sortBy == MANUAL) true else sortAsc
            Pager(pagingConfig) {
                playlistMediaDao.getPlaylistMediaListPaging(
                    playlistId = playlistId,
                    orderByColumnName = sortByColumnName,
                    asc = realSortAsc,
                )
            }.flow.map { pagingData ->
                pagingData.map { withContext(Dispatchers.IO) { it.updateMediaMetadata() } }
            }
        }.flowOn(Dispatchers.IO)
    }

    override fun requestPlaylistMediaList(
        playlistId: String
    ): Flow<List<PlaylistMediaWithArticleBean>> = flow {
        val playlist = playlistMediaDao.getPlaylistMediaList(playlistId)
        emit(playlist.map { withContext(Dispatchers.IO) { it.updateMediaMetadata() } })
    }.flowOn(Dispatchers.IO)

    override fun getCommonPlaylists(
        medias: List<PlaylistMediaWithArticleBean>
    ): Flow<List<String>> = playlistMediaDao.getCommonMediaPlaylistIdList(
        medias.map { it.playlistMediaBean.url },
        medias.size
    ).flowOn(Dispatchers.IO)

    fun insertPlaylistMedia(playlistId: String, url: String, articleId: String?): Flow<Boolean> =
        flow {
            if (playlistDao.exists(playlistId) == 0 ||
                playlistMediaDao.exists(playlistId = playlistId, url = url) != 0
            ) {
                emit(false)
                return@flow
            }
            val orderPosition = playlistMediaDao.getMaxOrder(playlistId = playlistId) + ORDER_DELTA
            val realArticleId = articleId?.takeIf { articleDao.exists(it) > 0 }
            playlistMediaDao.insertPlaylistMedia(
                PlaylistMediaBean(
                    playlistId = playlistId,
                    url = url,
                    articleId = realArticleId,
                    orderPosition = orderPosition,
                    createTime = System.currentTimeMillis(),
                )
            )
            emit(true)
        }.flowOn(Dispatchers.IO)

    override fun insertPlaylistMedias(
        toPlaylistId: String,
        medias: List<PlaylistMediaWithArticleBean>
    ): Flow<Unit> = flow {
        if (playlistDao.exists(toPlaylistId) == 0) {
            emit(Unit)
            return@flow
        }
        medias.forEach {
            insertPlaylistMedia(
                playlistId = toPlaylistId,
                url = it.playlistMediaBean.url,
                articleId = it.playlistMediaBean.articleId,
            ).collect()
        }
        emit(Unit)
    }.flowOn(Dispatchers.IO)

    override fun removeMediaFromPlaylist(
        mediaList: List<PlaylistMediaWithArticleBean>,
    ): Flow<Int> = flow {
        emit(playlistMediaDao.deletePlaylistMediaByIdAndUrl(playlist = mediaList))
    }.flowOn(Dispatchers.IO)

    fun reorderPlaylistMedia(
        playlistId: String,
        fromIndex: Int,
        toIndex: Int,
    ): Flow<Int> = flow {
        if (fromIndex == toIndex) {
            emit(0)
            return@flow
        }
        val fromMedia = playlistMediaDao.getNth(playlistId, fromIndex)
        if (fromMedia == null) {
            emit(0)
            return@flow
        }
        val prevMedia = if (fromIndex < toIndex) {
            playlistMediaDao.getNth(playlistId, toIndex)
        } else {
            if (toIndex - 1 >= 0) {
                playlistMediaDao.getNth(playlistId, toIndex - 1)
            } else null
        }

        val prevOrder: Double
        val nextOrder: Double

        if (prevMedia == null) {  // Insert to first
            val minOrder = playlistMediaDao.getMinOrder(playlistId = playlistId)
            prevOrder = minOrder - ORDER_DELTA * 2
            nextOrder = minOrder
        } else {
            val nextMedia = playlistMediaDao.getNth(
                playlistId = playlistId,
                index = if (fromIndex < toIndex) toIndex + 1 else toIndex,
            )
            if (nextMedia == null) {
                val maxOrder = playlistMediaDao.getMaxOrder(playlistId = playlistId)
                prevOrder = maxOrder
                nextOrder = maxOrder + ORDER_DELTA * 2
            } else {
                prevOrder = prevMedia.playlistMediaBean.orderPosition
                nextOrder = nextMedia.playlistMediaBean.orderPosition
            }
        }
        if (nextOrder - prevOrder < ORDER_MIN_DELTA * 2) {
            playlistMediaDao.reindexOrders(playlistId = playlistId)
            emit(
                reorderPlaylistMedia(
                    playlistId = playlistId,
                    fromIndex = fromIndex,
                    toIndex = toIndex,
                ).first()
            )
            return@flow
        } else {
            emit(
                playlistMediaDao.reorderPlaylistMedia(
                    playlistId = playlistId,
                    url = fromMedia.playlistMediaBean.url,
                    orderPosition = (prevOrder + nextOrder) / 2.0,
                )
            )
        }
    }.flowOn(Dispatchers.IO)
}