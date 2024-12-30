package com.skyd.anivu.ui.screen.more

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.skyd.anivu.R
import com.skyd.anivu.ext.isCompact
import com.skyd.anivu.ext.plus
import com.skyd.anivu.model.bean.MoreBean
import com.skyd.anivu.ui.component.PodAuraTopBar
import com.skyd.anivu.ui.component.PodAuraTopBarStyle
import com.skyd.anivu.ui.component.shape.CloverShape
import com.skyd.anivu.ui.component.shape.CurlyCornerShape
import com.skyd.anivu.ui.component.shape.RoundedCornerStarShape
import com.skyd.anivu.ui.component.shape.SquircleShape
import com.skyd.anivu.ui.local.LocalNavController
import com.skyd.anivu.ui.local.LocalWindowSizeClass
import com.skyd.anivu.ui.screen.about.ABOUT_SCREEN_ROUTE
import com.skyd.anivu.ui.screen.download.openDownloadScreen
import com.skyd.anivu.ui.screen.settings.SETTINGS_SCREEN_ROUTE
import com.skyd.anivu.ui.screen.settings.data.importexport.IMPORT_EXPORT_SCREEN_ROUTE


const val MORE_SCREEN_ROUTE = "moreScreen"

@Composable
fun MoreScreen() {
    val navController = LocalNavController.current
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val density = LocalDensity.current
    val windowSizeClass = LocalWindowSizeClass.current

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            PodAuraTopBar(
                style = PodAuraTopBarStyle.Small,
                title = { Text(text = stringResource(id = R.string.more_screen_name)) },
                navigationIcon = {},
                windowInsets = WindowInsets.safeDrawing.only(
                    (WindowInsetsSides.Top + WindowInsetsSides.Right).run {
                        if (windowSizeClass.isCompact) plus(WindowInsetsSides.Left) else this
                    }
                )
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(
            (WindowInsetsSides.Top + WindowInsetsSides.Right).run {
                if (windowSizeClass.isCompact) plus(WindowInsetsSides.Left) else this
            }
        )
    ) {
        val colorScheme: ColorScheme = MaterialTheme.colorScheme
        val dataList = remember(context, colorScheme, density, navController) {
            getMoreBeanList(context, colorScheme, density, navController)
        }
        LazyVerticalGrid(
            modifier = Modifier.fillMaxSize(),
            contentPadding = it + PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            columns = GridCells.Adaptive(130.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(dataList) { item ->
                More1Item(
                    data = item,
                    onClickListener = { data -> data.action.invoke() }
                )
            }
        }
    }
}

@Composable
fun More1Item(
    data: MoreBean,
    onClickListener: ((data: MoreBean) -> Unit)? = null
) {
    OutlinedCard(shape = RoundedCornerShape(16)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        onClickListener?.invoke(data)
                    }
                )
                .padding(25.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .padding(5.dp)
                    .background(
                        color = data.shapeColor,
                        shape = data.shape
                    )
                    .padding(16.dp)
            ) {
                Icon(
                    modifier = Modifier.size(35.dp),
                    imageVector = data.icon,
                    contentDescription = null,
                    tint = data.iconTint
                )
            }
            Text(
                modifier = Modifier
                    .padding(horizontal = 5.dp)
                    .padding(top = 15.dp)
                    .basicMarquee(iterations = Int.MAX_VALUE),
                text = data.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun getMoreBeanList(
    context: Context,
    colorScheme: ColorScheme,
    density: Density,
    navController: NavController,
): MutableList<MoreBean> {
    return mutableListOf(
        MoreBean(
            title = context.getString(R.string.download_screen_name),
            icon = Icons.Outlined.Download,
            iconTint = colorScheme.onPrimary,
            shape = RoundedCornerStarShape,
            shapeColor = colorScheme.primary,
            action = { openDownloadScreen(navController) },
        ),
        MoreBean(
            title = context.getString(R.string.import_export_screen_name),
            icon = Icons.Outlined.SwapVert,
            iconTint = colorScheme.onSecondary,
            shape = CloverShape,
            shapeColor = colorScheme.secondary,
            action = { navController.navigate(IMPORT_EXPORT_SCREEN_ROUTE) },
        ),
        MoreBean(
            title = context.getString(R.string.settings_screen_name),
            icon = Icons.Outlined.Settings,
            iconTint = colorScheme.onTertiary,
            shape = SquircleShape,
            shapeColor = colorScheme.tertiary,
            action = { navController.navigate(SETTINGS_SCREEN_ROUTE) },
        ),
        MoreBean(
            title = context.getString(R.string.about_screen_name),
            icon = Icons.Outlined.Info,
            iconTint = colorScheme.onPrimary,
            shape = CurlyCornerShape(amp = with(density) { 1.6.dp.toPx() }, count = 10),
            shapeColor = colorScheme.primary,
            action = { navController.navigate(ABOUT_SCREEN_ROUTE) }
        ),
    )
}