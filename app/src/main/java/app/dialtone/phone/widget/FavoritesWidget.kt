package app.dialtone.phone.widget

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.GridCells
import androidx.glance.appwidget.lazy.LazyVerticalGrid
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import app.dialtone.phone.data.ContactsRepository
import app.dialtone.phone.util.hasPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Favorites widget: your starred contacts as a grid of faces; tap one to call them.
 *
 * Same people and order as the Favorites row on Home (alphabetical, so faces stay where you
 * expect them), calling their first number as Home does. The number of columns follows the
 * widget's width; rows scroll when there are more favorites than fit.
 */
class FavoritesWidget : GlanceAppWidget() {

    // Exact: the launcher tells us the real size, so the column count can follow it.
    override val sizeMode = SizeMode.Exact

    private class Favorite(val id: Long, val name: String, val number: String, val photo: Bitmap?)

    /** What the widget shows; [needsSetup] when Dialtone can't read contacts yet. */
    private class State(val favorites: List<Favorite>, val needsSetup: Boolean)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme { Content() }
        }
    }

    @Composable
    private fun Content() {
        val context = LocalContext.current
        val tick by Widgets.tick.collectAsState()
        val state by produceState<State?>(null, tick) { value = load(context) }
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .cornerRadius(24.dp)
                .background(GlanceTheme.colors.widgetBackground)
                .padding(WidgetPadding),
            contentAlignment = Alignment.Center,
        ) {
            val s = state ?: return@Box
            when {
                s.needsSetup -> Message("Open Dialtone to finish setup", WidgetUi.openApp(context))
                s.favorites.isEmpty() -> Message("No favorites yet.\nTap to add some in Dialtone.", WidgetUi.openApp(context))
                else -> Grid(s.favorites)
            }
        }
    }

    @Composable
    private fun Grid(favorites: List<Favorite>) {
        val context = LocalContext.current
        val width = LocalSize.current.width - WidgetPadding * 2
        val columns = (width.value / CELL_WIDTH).toInt().coerceIn(1, 6)
        LazyVerticalGrid(gridCells = GridCells.Fixed(columns), modifier = GlanceModifier.fillMaxSize()) {
            items(favorites, itemId = { it.id }) { fav ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clickable(actionStartActivity(WidgetUi.quickCall(context, fav.number))),
                ) {
                    WidgetAvatar(fav.name, fav.photo, AVATAR.dp)
                    Spacer(GlanceModifier.height(4.dp))
                    Text(
                        fav.name.substringBefore(' '),
                        maxLines = 1,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                        ),
                    )
                }
            }
        }
    }

    @Composable
    private fun Message(text: String, open: android.content.Intent) {
        Text(
            text,
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp, textAlign = TextAlign.Center),
            modifier = GlanceModifier.fillMaxWidth().clickable(actionStartActivity(open)),
        )
    }

    private suspend fun load(context: Context): State = withContext(Dispatchers.IO) {
        if (!context.hasPermission(Manifest.permission.READ_CONTACTS)) return@withContext State(emptyList(), true)
        val px = WidgetUi.dpToPx(context, AVATAR.dp)
        val favorites = ContactsRepository(context).load()
            .filter { it.starred }
            .mapNotNull { c ->
                val number = c.numbers.firstOrNull()?.number ?: return@mapNotNull null
                Favorite(c.id, c.name, number, WidgetUi.loadPhoto(context, c.photoUri, px))
            }
            // Bitmaps add up; a huge favorites list would overflow what a widget can carry.
            .take(MAX_FAVORITES)
        State(favorites, false)
    }

    private companion object {
        const val CELL_WIDTH = 72f
        const val AVATAR = 48
        const val MAX_FAVORITES = 30
    }
}

/** Registers [FavoritesWidget] with the launcher's widget picker. */
class FavoritesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FavoritesWidget()
}
