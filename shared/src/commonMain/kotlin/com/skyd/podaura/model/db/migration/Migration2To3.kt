package com.skyd.podaura.model.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.skyd.podaura.model.bean.download.bt.BtDownloadInfoBean
import com.skyd.podaura.model.bean.download.bt.DOWNLOAD_INFO_TABLE_NAME
import com.skyd.podaura.model.bean.download.bt.TORRENT_FILE_TABLE_NAME
import com.skyd.podaura.model.bean.download.bt.TorrentFileBean

class Migration2To3 : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
                CREATE TABLE $TORRENT_FILE_TABLE_NAME (
                    ${TorrentFileBean.LINK_COLUMN} TEXT NOT NULL,
                    ${TorrentFileBean.PATH_COLUMN} TEXT NOT NULL,
                    ${TorrentFileBean.SIZE_COLUMN} INTEGER NOT NULL,
                    PRIMARY KEY (${TorrentFileBean.LINK_COLUMN}, ${TorrentFileBean.PATH_COLUMN})
                    FOREIGN KEY (${TorrentFileBean.LINK_COLUMN})
                                REFERENCES $DOWNLOAD_INFO_TABLE_NAME(${BtDownloadInfoBean.LINK_COLUMN})
                                ON DELETE CASCADE
                )
                """
        )
    }
}