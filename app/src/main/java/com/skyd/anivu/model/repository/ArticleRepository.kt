package com.skyd.anivu.model.repository

import android.database.DatabaseUtils
import android.os.Parcelable
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.sqlite.db.SimpleSQLiteQuery
import com.skyd.anivu.base.BaseRepository
import com.skyd.anivu.model.bean.article.ARTICLE_TABLE_NAME
import com.skyd.anivu.model.bean.article.ArticleBean
import com.skyd.anivu.model.bean.article.ArticleWithFeed
import com.skyd.anivu.model.bean.group.GroupVo
import com.skyd.anivu.model.db.dao.ArticleDao
import com.skyd.anivu.model.db.dao.FeedDao
import com.skyd.anivu.ui.component.showToast
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.parcelize.Parcelize
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

@Parcelize
sealed class ArticleSort(open val asc: Boolean) : Parcelable {
    data class Date(override val asc: Boolean) : ArticleSort(asc)
    data class Title(override val asc: Boolean) : ArticleSort(asc)

    companion object {
        val default = Date(false)
    }
}

class ArticleRepository @Inject constructor(
    private val feedDao: FeedDao,
    private val articleDao: ArticleDao,
    private val rssHelper: RssHelper,
    private val pagingConfig: PagingConfig,
) : BaseRepository() {
    private val filterFavorite = MutableStateFlow<Boolean?>(null)
    private val filterRead = MutableStateFlow<Boolean?>(null)
    private val articleSortDateDesc = MutableStateFlow<ArticleSort>(ArticleSort.default)

    fun filterFavorite(favorite: Boolean?) {
        filterFavorite.value = favorite
    }

    fun filterRead(read: Boolean?) {
        filterRead.value = read
    }

    fun updateSort(articleSort: ArticleSort) {
        articleSortDateDesc.value = articleSort
    }

    fun requestArticleList(
        feedUrls: List<String>,
        articleIds: List<String>
    ): Flow<PagingData<ArticleWithFeed>> {
        return combine(
            filterFavorite,
            filterRead,
            articleSortDateDesc,
        ) { favorite, read, sortDateDesc ->
            arrayOf(favorite, read, sortDateDesc)
        }.flatMapLatest { (favorite, read, sortDateDesc) ->
            Pager(pagingConfig) {
                articleDao.getArticlePagingSource(
                    genSql(
                        feedUrls = feedUrls,
                        articleIds = articleIds,
                        isFavorite = favorite as Boolean?,
                        isRead = read as Boolean?,
                        orderBy = sortDateDesc as ArticleSort,
                    )
                )
            }.flow
        }.flowOn(Dispatchers.IO)
    }

    fun refreshGroupArticles(groupId: String?): Flow<Unit> {
        return flow {
            val realGroupId = if (groupId == GroupVo.DEFAULT_GROUP_ID) null else groupId
            emit(feedDao.getFeedsByGroupId(realGroupId).map { it.feed.url })
        }.flatMapConcat {
            refreshArticleList(feedUrls = it)
        }.flowOn(Dispatchers.IO)
    }

    fun refreshArticleList(feedUrls: List<String>): Flow<Unit> = flow {
        coroutineScope {
            val requests = mutableListOf<Deferred<Unit>>()
            feedUrls.forEach { feedUrl ->
                requests += async {
                    val articleBeanList = runCatching {
                        rssHelper.queryRssXml(
                            feed = feedDao.getFeed(feedUrl).feed,
                            latestLink = articleDao.queryLatestByFeedUrl(feedUrl)?.link,
                        )?.also { feedWithArticle ->
                            feedDao.updateFeed(feedWithArticle.feed)
                        }?.articles
                    }.onFailure { e ->
                        if (e !is CancellationException) {
                            e.printStackTrace()
                            (feedUrl + "\n" + e.message).showToast()
                        }
                    }.getOrNull()

                    if (articleBeanList.isNullOrEmpty()) return@async

                    articleDao.insertListIfNotExist(articleBeanList.map { articleWithEnclosure ->
                        if (articleWithEnclosure.article.feedUrl != feedUrl) {
                            articleWithEnclosure.copy(
                                article = articleWithEnclosure.article.copy(feedUrl = feedUrl)
                            )
                        } else articleWithEnclosure
                    })
                }
            }
            requests.forEach { it.await() }
            emit(Unit)
        }
    }.flowOn(Dispatchers.IO)

    fun favoriteArticle(articleId: String, favorite: Boolean): Flow<Unit> {
        return flow {
            emit(articleDao.favoriteArticle(articleId, favorite))
        }.flowOn(Dispatchers.IO)
    }

    fun readArticle(articleId: String, read: Boolean): Flow<Unit> {
        return flow {
            emit(articleDao.readArticle(articleId, read))
        }.flowOn(Dispatchers.IO)
    }

    companion object {
        fun genSql(
            feedUrls: List<String>,
            articleIds: List<String>,
            isFavorite: Boolean?,
            isRead: Boolean?,
            orderBy: ArticleSort,
        ): SimpleSQLiteQuery {
            val sql = buildString {
                append("SELECT DISTINCT * FROM `$ARTICLE_TABLE_NAME` WHERE 1 ")
                if (isFavorite != null) {
                    append("AND `${ArticleBean.IS_FAVORITE_COLUMN}` = ${if (isFavorite) 1 else 0} ")
                }
                if (isRead != null) {
                    append("AND `${ArticleBean.IS_READ_COLUMN}` = ${if (isRead) 1 else 0} ")
                }
                if (feedUrls.isEmpty()) {
                    append("AND (0 ")
                } else {
                    val feedUrlsStr = feedUrls.joinToString(", ") {
                        DatabaseUtils.sqlEscapeString(it)
                    }
                    append("AND (`${ArticleBean.FEED_URL_COLUMN}` IN ($feedUrlsStr) ")
                }
                if (articleIds.isNotEmpty()) {
                    val articleIdsStr = articleIds.joinToString(", ") {
                        DatabaseUtils.sqlEscapeString(it)
                    }
                    append("OR `${ArticleBean.ARTICLE_ID_COLUMN}` IN ($articleIdsStr) ")
                }
                append(") ")
                val ascOrDesc = if (orderBy.asc) "ASC" else "DESC"
                val orderField = when (orderBy) {
                    is ArticleSort.Date -> ArticleBean.DATE_COLUMN
                    is ArticleSort.Title -> ArticleBean.TITLE_COLUMN
                }
                append("\nORDER BY `$orderField` $ascOrDesc")
            }
            return SimpleSQLiteQuery(sql)
        }
    }
}