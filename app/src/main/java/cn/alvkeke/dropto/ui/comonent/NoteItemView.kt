package cn.alvkeke.dropto.ui.comonent

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import cn.alvkeke.dropto.R
import cn.alvkeke.dropto.data.AttachmentFile
import cn.alvkeke.dropto.storage.ImageLoader
import cn.alvkeke.dropto.ui.activity.ShareRecvActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * NoteItemView - the note item of the note list, rendered with Jetpack Compose in a
 * discord-like style:
 *
 *  ```
 *  (avatar) Sender name     HH:mm  [status icons]
 *  (avatar) message text, full width, no bubble
 *  (avatar) [ media strip: one horizontally scrollable row showing every media tile ]
 *  (avatar) [ file attachment rows ]
 *  (avatar) [ reaction pills ]
 *  ```
 *
 * This view is a thin Android container ([FrameLayout] hosting a [ComposeView]) that
 * plugs into the existing RecyclerView architecture unchanged: the RecyclerView level
 * touch listener resolves taps through [checkClickedContent], so the surrounding
 * gesture handling (scroll / long press / slide select / popup cards) keeps working.
 * The compose content is intentionally non-interactive except for the horizontal media
 * strip (it scrolls on horizontal drags; vertical drags and taps still reach the list).
 *
 * The adapter binds data by setting these fields (all Compose state, so a bind
 * immediately recomposes the content):
 *  ```
 *  view.text = note.text; view.sender = note.sender; ...
 *  view.reactionList.clear()/addAll(...)
 *  view.medias.clear()/addAll(...); view.files.clear()/addAll(...)
 *  ```
 *
 * Tap-target bookkeeping: every interactive region reports its bounds (in item-local
 * coordinates) into [mediaRects] / [fileRects] / [reactionRects] / [avatarRect] during
 * layout, which keeps [checkClickedContent] accurate even while the list scrolls.
 */
class NoteItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs), SelectableRecyclerView.HighlightAble {

    /** RecyclerView adapter position, useful for debugging. */
    var index: Int = -1

    var text: String by mutableStateOf("")
    var createTime: Long by mutableLongStateOf(0L)
    var isEdited: Boolean by mutableStateOf(false)
    var isDeleted: Boolean by mutableStateOf(false)
    var isSynced: Boolean by mutableStateOf(false)
    var sender: String? by mutableStateOf(null)

    val reactionList: MutableList<String> = mutableStateListOf()
    val medias: MutableList<AttachmentFile> = mutableStateListOf()
    val files: MutableList<AttachmentFile> = mutableStateListOf()

    // ---- geometry bookkeeping (filled by Compose during layout, used by hit testing) ----

    /** Tap rects of the visible media tiles, in item-local coordinates. */
    internal val mediaRects: MutableList<RectF> = ArrayList()

    /** Tap rects of the visible file rows, in item-local coordinates. */
    internal val fileRects: MutableList<RectF> = ArrayList()

    /** Tap rects of the reaction pills, in item-local coordinates. */
    internal val reactionRects: MutableList<RectF> = ArrayList()

    /** Tap rect of the sender avatar, in item-local coordinates (empty when no sender). */
    internal val avatarRect: RectF = RectF()

    /** Layout coordinates of the root content box, used to translate window coords to local. */
    internal var rootCoordinates: LayoutCoordinates? = null

    private val composeContent: ComposeView = ComposeView(context).apply {
        // Disposed on every recycle via [releaseContentComposition] so a rebind composes from
        // scratch (correct WRAP_CONTENT height on first measure); this strategy only handles the
        // final cleanup when the view is detached/discarded.
        setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool
        )
        setContent { NoteItemContent(this@NoteItemView) }
    }

    init {
        addView(
            composeContent,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT,
            ),
        )
    }

    /**
     * Drop the composition when RecyclerView recycles this item, so the next bind composes from
     * scratch. With RecyclerView 1.3+ a pooled ComposeView keeps its composition (and the previous
     * note's measured height) alive; without disposal the rebind only updates on a later
     * recomposition frame and RecyclerView measures a stale WRAP_CONTENT height - fast scrolling
     * then jumps on every reused item.
     */
    fun releaseContentComposition() {
        composeContent.disposeComposition()
    }

    /**
     * Ask the inner Compose surface to re-measure right after binding so the item height is
     * settled in the current layout pass instead of one frame later (Google b/240449681
     * workaround).
     */
    fun settleContentLayout() {
        composeContent.getChildAt(0)?.requestLayout()
    }

    /**
     * Map a tap position (relative to this view) to the content under it.
     * Semantics are identical to the old canvas-drawn implementation.
     */
    fun checkClickedContent(x: Float, y: Float): ClickedContent {
        sender?.let {
            if (!avatarRect.isEmpty && avatarRect.contains(x, y)) {
                return ClickedContent(ClickedContent.Type.SENDER_ICON)
            }
        }
        for (i in mediaRects.indices) {
            if (mediaRects[i].contains(x, y)) {
                val media = medias.getOrNull(i) ?: break
                return ClickedContent(ClickedContent.Type.MEDIA, media, i)
            }
        }
        for (i in fileRects.indices) {
            if (fileRects[i].contains(x, y)) {
                val file = files.getOrNull(i) ?: break
                return ClickedContent(ClickedContent.Type.FILE, file, medias.size + i)
            }
        }
        for (i in reactionRects.indices) {
            if (reactionRects[i].contains(x, y)) {
                return ClickedContent(ClickedContent.Type.REACTION, null, i)
            }
        }
        return ClickedContent(ClickedContent.Type.BACKGROUND)
    }

    private val highlightPath = Path()

    override fun getHighlightArea(): Path {
        highlightPath.reset()
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return highlightPath
        val radius = resources.displayMetrics.density * HIGHLIGHT_RADIUS_DP
        highlightPath.addRoundRect(
            0f, 0f, w, h,
            radius, radius,
            Path.Direction.CW,
        )
        return highlightPath
    }

    /** Mirror of the legacy ClickedContent, so the fragment touch handler keeps working. */
    class ClickedContent(
        val type: Type,
        val data: AttachmentFile? = null,
        val index: Int = -1,
    ) {
        enum class Type {
            BACKGROUND,
            SENDER_ICON,
            MEDIA,
            FILE,
            REACTION,
        }
    }

    companion object {
        /** Maximum number of file rows rendered; the extra ones are counted on the "+n files" row. */
        const val MAX_FILE_COUNT = 4

        /** Highlight corner radius (dp). */
        private const val HIGHLIGHT_RADIUS_DP = 8f
    }
}

// ============================================================================================
//  Compose content
// ============================================================================================

/**
 * The root Compose content of [NoteItemView].
 * The content is intentionally non-interactive: all taps are still resolved by the
 * RecyclerView level touch listener through [NoteItemView.checkClickedContent], so the
 * surrounding gesture handling (scroll / long press / slide select) keeps working.
 */
@Composable
private fun NoteItemContent(view: NoteItemView) {
    val context = LocalContext.current
    val palette = neoPalette()

    // read the bind data to subscribe to it; geometry lists are kept in sync below
    val text = view.text
    val isDeleted = view.isDeleted
    val reactions = view.reactionList
    val files = view.files
    val medias = view.medias

    ensureRectCount(view.mediaRects, medias.size)
    ensureRectCount(view.fileRects, files.size.coerceAtMost(NoteItemView.MAX_FILE_COUNT))
    ensureRectCount(view.reactionRects, reactions.size)

    Box(
        Modifier
            .fillMaxWidth()
            .onGloballyPositioned { view.rootCoordinates = it },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = ITEM_PADDING_H,
                    top = ITEM_PADDING_V,
                    end = ITEM_PADDING_H,
                    bottom = ITEM_PADDING_V,
                ),
            verticalAlignment = Alignment.Top,
        ) {
            AvatarBox(view, palette)
            Spacer(Modifier.width(AVATAR_GAP))
            Column(Modifier.weight(1f)) {
                HeaderRow(view, palette, context)

                if (text.isNotEmpty()) {
                    Text(
                        text = text,
                        color = if (isDeleted) {
                            palette.textMain.copy(alpha = 0.4f)
                        } else {
                            palette.textMain
                        },
                        fontSize = CONTENT_TEXT_SIZE,
                        lineHeight = CONTENT_LINE_HEIGHT,
                        textDecoration = if (isDeleted) TextDecoration.LineThrough else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = CONTENT_TOP_PADDING),
                    )
                }

                MediaGallery(view, palette)
                FileList(view, palette)
                ReactionBar(view, palette)
            }
        }
    }
}

// --------------------------------------------------------------------------------------------
// header: avatar is separate, this is the name + time + status row
// --------------------------------------------------------------------------------------------

@Composable
private fun HeaderRow(view: NoteItemView, palette: NeoPalette, context: Context) {
    val sender = view.sender
    val name = remember(sender, context) { resolveSenderLabel(context, sender) }
    val nameColor = if (sender == null) palette.textMain else palette.senderName
    val isDeleted = view.isDeleted

    val icons = remember(view.isDeleted, view.isSynced, view.isEdited, palette, context) {
        val color = palette.textSub.toArgb()
        buildList {
            if (view.isDeleted) {
                add(cachedStatusIcon(context, R.drawable.icon_common_remove, color))
            }
            if (!view.isSynced) {
                add(cachedStatusIcon(context, R.drawable.icon_common_not_sync, color))
            }
            if (view.isEdited) {
                add(cachedStatusIcon(context, R.drawable.icon_common_edit, color))
            }
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = name,
            color = if (isDeleted) nameColor.copy(alpha = 0.4f) else nameColor,
            fontSize = NAME_TEXT_SIZE,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(NAME_TIME_GAP))
        Text(
            text = formatNoteTime(view.createTime),
            color = if (isDeleted) palette.textSub.copy(alpha = 0.4f) else palette.textSub,
            fontSize = TIME_TEXT_SIZE,
            maxLines = 1,
        )
        if (icons.isNotEmpty()) {
            Spacer(Modifier.width(TIME_ICON_GAP))
            Row(verticalAlignment = Alignment.CenterVertically) {
                icons.forEach { icon ->
                    Image(
                        bitmap = icon.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .padding(start = STATUS_ICON_GAP)
                            .size(STATUS_ICON_SIZE),
                    )
                }
            }
        }
    }
}

@Composable
private fun AvatarBox(view: NoteItemView, palette: NeoPalette) {
    val sender = view.sender
    val avatar = rememberSenderAvatar(sender)
    Box(
        modifier = Modifier
            .size(AVATAR_SIZE)
            .clip(CircleShape)
            .background(palette.avatarBackground)
            .onGloballyPositioned { coords ->
                if (sender != null) {
                    view.updateRectFrom(coords, view.avatarRect)
                } else {
                    view.avatarRect.setEmpty()
                }
            },
    ) {
        Image(
            bitmap = avatar.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

// --------------------------------------------------------------------------------------------
// media strip: every media in one horizontally scrollable row of fixed-height tiles
// --------------------------------------------------------------------------------------------

@Composable
private fun MediaGallery(view: NoteItemView, palette: NeoPalette) {
    val medias = view.medias
    if (medias.isEmpty()) return

    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(top = SECTION_TOP_PADDING),
        horizontalArrangement = Arrangement.spacedBy(MEDIA_GAP),
    ) {
        medias.forEachIndexed { index, media ->
            MediaTile(
                file = media,
                index = index,
                view = view,
                palette = palette,
            )
        }
    }
}

@Composable
private fun MediaTile(
    file: AttachmentFile,
    index: Int,
    view: NoteItemView,
    palette: NeoPalette,
) {
    val thumbnail = rememberAttachmentThumb(file)
    val tileWidth = remember(file.md5) { mediaTileWidth(file) }
    Box(
        modifier = Modifier
            .width(tileWidth)
            .height(MEDIA_HEIGHT)
            .clip(RoundedCornerShape(MEDIA_RADIUS))
            .background(palette.tilePlaceholder)
            .onGloballyPositioned { coords ->
                view.updateRectFrom(coords, view.mediaRects[index])
            },
    ) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail.asImageBitmap(),
                contentDescription = file.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (file.isVideo &&
                thumbnail !== ImageLoader.errorBitmap &&
                thumbnail !== ImageLoader.loadingBitmap
            ) {
                VideoGlyph(palette)
            }
        }
    }
}

/** Video play glyph (translucent circle + triangle), placed over the loaded thumbnail. */
@Composable
private fun VideoGlyph(palette: NeoPalette) {
    Canvas(Modifier.fillMaxSize()) {
        val side = minOf(size.width, size.height)
        val radius = side * 0.30f
        val strokeWidth = (side * 0.03f).coerceAtLeast(2f)
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(palette.videoScrim, radius = radius, center = center)
        drawCircle(
            palette.onVideoScrim,
            radius = radius,
            center = center,
            style = Stroke(width = strokeWidth),
        )
        val triHalf = radius * 0.45f
        val triHeight = radius * 1.1f
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(center.x - triHalf * 0.7f, center.y - triHeight / 2f)
            lineTo(center.x + triHalf * 0.9f, center.y)
            lineTo(center.x - triHalf * 0.7f, center.y + triHeight / 2f)
            close()
        }
        drawPath(path, palette.onVideoScrim)
    }
}

// --------------------------------------------------------------------------------------------
// file attachments
// --------------------------------------------------------------------------------------------

@Composable
private fun FileList(view: NoteItemView, palette: NeoPalette) {
    val files = view.files
    if (files.isEmpty()) return

    val maxFiles = NoteItemView.MAX_FILE_COUNT
    val shown = files.take(maxFiles)
    val extraFiles = files.size - shown.size
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = SECTION_TOP_PADDING),
        verticalArrangement = Arrangement.spacedBy(FILE_ROW_GAP),
    ) {
        shown.forEachIndexed { index, file ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coords ->
                        view.updateRectFrom(coords, view.fileRects[index])
                    }
                    .background(
                        palette.fileCardBackground,
                        RoundedCornerShape(FILE_CARD_RADIUS),
                    )
                    .border(FILE_CARD_BORDER, palette.fileCardBorder, RoundedCornerShape(FILE_CARD_RADIUS))
                    .padding(FILE_CARD_PADDING),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(FILE_CARD_ICON_SIZE)
                        .clip(RoundedCornerShape(FILE_CARD_ICON_RADIUS))
                        .background(palette.reactionBackground),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "↗",
                        color = palette.onReaction,
                        fontSize = FILE_CARD_ICON_TEXT_SIZE,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.width(FILE_TEXT_GAP))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = file.name,
                        color = palette.fileLink,
                        fontSize = FILE_NAME_TEXT_SIZE,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (file.mimeType.isNotEmpty()) {
                        Text(
                            text = file.mimeType,
                            color = palette.textSub,
                            fontSize = FILE_MIME_TEXT_SIZE,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (index == shown.lastIndex && extraFiles > 0) {
                    Text(
                        text = "+$extraFiles",
                        color = palette.textSub,
                        fontSize = FILE_EXTRA_TEXT_SIZE,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

// --------------------------------------------------------------------------------------------
// reactions
// --------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReactionBar(view: NoteItemView, palette: NeoPalette) {
    val reactions = view.reactionList
    if (reactions.isEmpty()) return

    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = REACTION_TOP_PADDING),
        horizontalArrangement = Arrangement.spacedBy(REACTION_GAP),
        verticalArrangement = Arrangement.spacedBy(REACTION_GAP),
    ) {
        reactions.forEachIndexed { index, reaction ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(REACTION_RADIUS))
                    .background(palette.reactionBackground)
                    .onGloballyPositioned { coords ->
                        view.updateRectFrom(coords, view.reactionRects[index])
                    }
                    .padding(
                        horizontal = REACTION_PADDING_H,
                        vertical = REACTION_PADDING_V,
                    ),
            ) {
                Text(
                    text = reaction,
                    color = palette.onReaction,
                    fontSize = REACTION_TEXT_SIZE,
                )
            }
        }
    }
}

// --------------------------------------------------------------------------------------------
// helpers
// --------------------------------------------------------------------------------------------

/** Theme-resolved colors shared by the whole note item. */
@Immutable
private data class NeoPalette(
    val textMain: Color,
    val textSub: Color,
    val senderName: Color,
    val avatarBackground: Color,
    val tilePlaceholder: Color,
    val fileCardBackground: Color,
    val fileCardBorder: Color,
    val fileLink: Color,
    val reactionBackground: Color,
    val onReaction: Color,
    val videoScrim: Color,
    val onVideoScrim: Color,
)

/**
 * Build the palette from theme resources.
 *
 * Called on every composition (not cached with `remember`) so colors are re-read when the
 * composition re-evaluates after a configuration change: the app declares
 * `android:configChanges="uiMode"`, so toggling night mode does not recreate the activity
 * and a `ContextCompat.getColor` snapshot taken once would stay stale.
 */
@Composable
private fun neoPalette(): NeoPalette = NeoPalette(
    textMain = colorResource(R.color.color_text_main),
    textSub = colorResource(R.color.color_text_sub),
    senderName = colorResource(R.color.reaction_background),
    avatarBackground = colorResource(R.color.file_icon_background),
    tilePlaceholder = colorResource(R.color.note_bubble_background),
    fileCardBackground = colorResource(R.color.file_card_background),
    fileCardBorder = colorResource(R.color.file_card_border),
    fileLink = colorResource(R.color.file_link),
    reactionBackground = colorResource(R.color.reaction_background),
    onReaction = colorResource(R.color.more_media_overlay_text),
    videoScrim = colorResource(R.color.video_play_icon_background),
    onVideoScrim = colorResource(R.color.video_play_icon_foreground),
)

/** Load a thumbnail (image or video) for an attachment; null while loading / on failure. */
@Composable
private fun rememberAttachmentThumb(file: AttachmentFile): Bitmap? {
    var bitmap by remember(file.md5) { mutableStateOf<Bitmap?>(null) }
    DisposableEffect(file.md5) {
        var active = true
        fun accept(loaded: Bitmap?) {
            if (!active) return
            bitmap = if (loaded == null || loaded.isRecycled) ImageLoader.errorBitmap else loaded
        }
        val cached = if (file.isVideo) {
            ImageLoader.loadVideoThumbnailAsync(file.md5file, false) { accept(it) }
        } else {
            ImageLoader.loadImageAsync(file.md5file, false) { accept(it) }
        }
        if (cached != null) accept(cached)
        onDispose { active = false }
    }
    return bitmap
}

/** Aspect ratio (width / height) of a single media file, used to size the media strip tiles.
 * No cap is applied here: the final tile width is clamped in [mediaTileWidth],
 * so extreme aspect ratios can only widen a tile up to MEDIA_MAX_WIDTH.
 */
private fun mediaNaturalAspect(file: AttachmentFile): Float {
    if (file.isVideo) {
        // decoding video dimensions is slow, use a fixed ratio (like the old view)
        return MEDIA_MAX_WIDTH / MEDIA_HEIGHT
    }
    val size = cachedMediaSize(file) ?: return 1f
    if (size.first <= 0 || size.second <= 0) return 1f
    return size.first.toFloat() / size.second
}

/** Width of a media strip tile: the fixed tile height scaled by the natural aspect ratio,
 * clamped to [MEDIA_MIN_WIDTH, MEDIA_MAX_WIDTH] so very tall images never render as
 * thin slivers and very wide ones never exceed the 3:4 height/width maximum.
 */
private fun mediaTileWidth(file: AttachmentFile): Dp {
    val ratio = mediaNaturalAspect(file)
    return (MEDIA_HEIGHT * ratio).coerceIn(MEDIA_MIN_WIDTH, MEDIA_MAX_WIDTH)
}

/** Load the sender avatar: app icon of the sending package, own app icon for local notes. */
@Composable
private fun rememberSenderAvatar(sender: String?): Bitmap {
    val context = LocalContext.current
    val density = LocalDensity.current
    val px = with(density) { AVATAR_SIZE.toPx() }.roundToInt()
    return remember(sender, px) { cachedSenderAvatar(context, sender, px) }
}

/** Resolve the display name of a sender package (falls back to the package name). */
private fun resolveSenderLabel(context: Context, sender: String?): String {
    val key = sender ?: LOCAL_SENDER_LABEL
    senderLabelCache[key]?.let { return it }
    val label = when (sender) {
        null -> LOCAL_SENDER_LABEL
        ShareRecvActivity.UNKNOWN_SENDER_PACKAGE -> UNKNOWN_SENDER_LABEL
        else -> {
            val resolved = try {
                val info = context.packageManager.getApplicationInfo(sender, 0)
                context.packageManager.getApplicationLabel(info).toString()
            } catch (_: Exception) {
                null
            }
            resolved ?: sender
        }
    }
    senderLabelCache[key] = label
    return label
}

// --------------------------------------------------------------------------------------------
// object-level caches
// --------------------------------------------------------------------------------------------
// Compositions are recreated on every rebind ([releaseContentComposition]), so anything expensive
// would run again for every reused item during fast scrolling. Keys are bounded (package names,
// drawable+color pairs, md5-derived paths) and values are small.

private val statusIconCache = HashMap<Long, Bitmap>()

private fun cachedStatusIcon(context: Context, resId: Int, tintColor: Int): Bitmap {
    val key = (resId.toLong() shl 32) or (tintColor.toLong() and 0xFFFFFFFFL)
    return statusIconCache.getOrPut(key) {
        ImageLoader.loadDrawable(context, resId, tintColor)
    }
}

private val senderAvatarCache = HashMap<String, Bitmap>()

private fun cachedSenderAvatar(context: Context, sender: String?, px: Int): Bitmap {
    val cacheKey = (sender ?: LOCAL_SENDER_LABEL) + "@" + px
    senderAvatarCache[cacheKey]?.let { return it }
    val avatar = buildSenderAvatar(context, sender, px)
    senderAvatarCache[cacheKey] = avatar
    return avatar
}

private fun buildSenderAvatar(context: Context, sender: String?, px: Int): Bitmap {
    if (sender == ShareRecvActivity.UNKNOWN_SENDER_PACKAGE) {
        return ImageLoader.errorBitmap
    }
    return try {
        val drawable = if (sender == null) {
            context.packageManager.getApplicationIcon(context.packageName)
        } else {
            context.packageManager.getApplicationIcon(sender)
        }
        val bitmap = createBitmap(px, px)
        val canvas = AndroidCanvas(bitmap)
        drawable.setBounds(0, 0, px, px)
        drawable.draw(canvas)
        bitmap
    } catch (_: Exception) {
        ImageLoader.errorBitmap
    }
}

private val senderLabelCache = HashMap<String, String>()

private val mediaSizeCache = HashMap<String, Pair<Int, Int>>()

private const val MEDIA_SIZE_CACHE_LIMIT = 1024

private fun cachedMediaSize(file: AttachmentFile): Pair<Int, Int>? {
    val path = file.md5file.absolutePath
    if (mediaSizeCache.containsKey(path)) {
        return mediaSizeCache[path]
    }
    val size = ImageLoader.getImageSize(file.md5file)
    if (size != null) {
        if (mediaSizeCache.size >= MEDIA_SIZE_CACHE_LIMIT) {
            mediaSizeCache.clear()
        }
        mediaSizeCache[path] = size
    }
    return size
}

/**
 * Resize [rects] to [size], filling new entries with empty rects.
 * Called during composition so the tap-rect lists always match the bound data.
 */
private fun ensureRectCount(rects: MutableList<RectF>, size: Int) {
    while (rects.size < size) rects.add(RectF())
    while (rects.size > size) rects.removeAt(rects.lastIndex)
}

/**
 * Translate the window bounds of [coords] into coordinates local to this view
 * and write them into [out]. Used by hit-testing in checkClickedContent.
 */
private fun NoteItemView.updateRectFrom(coords: LayoutCoordinates, out: RectF) {
    val root = rootCoordinates ?: return
    if (root === coords) return
    val child = coords.boundsInWindow()
    val base = root.boundsInWindow()
    out.set(
        child.left - base.left,
        child.top - base.top,
        child.right - base.left,
        child.bottom - base.top,
    )
}

/** Format a note create time the same way as the old NoteItemView. */
private fun formatNoteTime(createTime: Long): String {
    if (createTime <= 0L) return ""
    val noteDate = Date(createTime)
    val now = Date()
    val calNote = Calendar.getInstance().apply { time = noteDate }
    val calNow = Calendar.getInstance().apply { time = now }

    val sameYear = calNote.get(Calendar.YEAR) == calNow.get(Calendar.YEAR)
    val dayOfYearNote = calNote.get(Calendar.DAY_OF_YEAR)
    val dayOfYearNow = calNow.get(Calendar.DAY_OF_YEAR)
    val diffDays = dayOfYearNow - dayOfYearNote

    return when {
        sameYear && diffDays == 0 -> fmtToday.format(noteDate)
        sameYear && diffDays == 1 -> "Yesterday " + fmtToday.format(noteDate)
        sameYear && diffDays in 2..6 -> fmtWeekly.format(noteDate)
        else -> fmtCommon.format(noteDate)
    }
}

// --------------------------------------------------------------------------------------------
// metrics & constants
// --------------------------------------------------------------------------------------------

private val AVATAR_SIZE = 40.dp
private val AVATAR_GAP = 10.dp
private val ITEM_PADDING_H = 10.dp
private val ITEM_PADDING_V = 6.dp

private val STATUS_ICON_SIZE = 14.dp
private val STATUS_ICON_GAP = 4.dp
private val NAME_TIME_GAP = 8.dp
private val TIME_ICON_GAP = 8.dp
private val NAME_TEXT_SIZE = 15.sp
private val TIME_TEXT_SIZE = 11.sp

private val CONTENT_TEXT_SIZE = 15.sp
private val CONTENT_LINE_HEIGHT = 21.sp
private val CONTENT_TOP_PADDING = 4.dp

private val SECTION_TOP_PADDING = 8.dp
private val MEDIA_GAP = 6.dp
private val MEDIA_RADIUS = 8.dp
private val MEDIA_HEIGHT = 180.dp
private val MEDIA_MIN_WIDTH = 100.dp
private val MEDIA_MAX_WIDTH = MEDIA_HEIGHT * 4f / 3f

private val FILE_ROW_GAP = 6.dp
private val FILE_CARD_RADIUS = 8.dp
private val FILE_CARD_BORDER = 1.dp
private val FILE_CARD_PADDING = 10.dp
private val FILE_CARD_ICON_SIZE = 36.dp
private val FILE_CARD_ICON_RADIUS = 6.dp
private val FILE_CARD_ICON_TEXT_SIZE = 18.sp
private val FILE_TEXT_GAP = 10.dp
private val FILE_NAME_TEXT_SIZE = 14.sp
private val FILE_MIME_TEXT_SIZE = 11.sp
private val FILE_EXTRA_TEXT_SIZE = 13.sp

private val REACTION_TOP_PADDING = 6.dp
private val REACTION_GAP = 6.dp
private val REACTION_RADIUS = 12.dp
private val REACTION_PADDING_H = 10.dp
private val REACTION_PADDING_V = 4.dp
private val REACTION_TEXT_SIZE = 13.sp

private const val LOCAL_SENDER_LABEL = "Me"
private const val UNKNOWN_SENDER_LABEL = "Unknown app"

private val fmtToday: SimpleDateFormat = SimpleDateFormat("HH:mm", Locale.CHINESE)
private val fmtWeekly: SimpleDateFormat = SimpleDateFormat("EEE HH:mm", Locale.CHINESE)
private val fmtCommon: SimpleDateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.CHINESE)
