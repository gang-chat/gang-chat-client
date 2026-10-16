package com.gangchat.client.app

import com.gangchat.client.protocol.Sticker
import com.gangchat.client.protocol.StickerPack
import com.gangchat.client.protocol.UploadedAsset

enum class StickerPanelSource { personal, room }

enum class StickerPanelBodyState { loading, error, empty, results }

class StickerPanelLoadState(
    val source: StickerPanelSource = StickerPanelSource.personal,
    val personalPacks: List<StickerPack> = emptyList(),
    val roomPacks: List<StickerPack> = emptyList(),
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
) {
    /**
     * [error] keeps Dart's sentinel default: omit it to keep the current
     * error, pass `null` to clear it.
     */
    fun copyWith(
        source: StickerPanelSource? = null,
        personalPacks: List<StickerPack>? = null,
        roomPacks: List<StickerPack>? = null,
        loading: Boolean? = null,
        loaded: Boolean? = null,
        error: Any? = _stickerPanelErrorUnchanged,
    ): StickerPanelLoadState {
        return StickerPanelLoadState(
            source = source ?: this.source,
            personalPacks = personalPacks ?: this.personalPacks,
            roomPacks = roomPacks ?: this.roomPacks,
            loading = loading ?: this.loading,
            loaded = loaded ?: this.loaded,
            error =
                if (error === _stickerPanelErrorUnchanged) {
                    this.error
                } else {
                    error as String?
                },
        )
    }
}

private val _stickerPanelErrorUnchanged: Any = Any()

fun stickerPanelStickers(
    source: StickerPanelSource,
    personalPacks: Iterable<StickerPack>,
    roomPacks: Iterable<StickerPack>,
): List<Sticker> {
    val packs =
        if (source == StickerPanelSource.personal) {
            personalPacks
        } else {
            roomPacks
        }
    return packs.flatMap { pack -> pack.stickers }
}

fun stickerPanelBodyState(
    loading: Boolean,
    error: String?,
    stickers: Iterable<Sticker>,
): StickerPanelBodyState {
    if (stickers.any()) return StickerPanelBodyState.results
    if (loading) return StickerPanelBodyState.loading
    if (error != null) return StickerPanelBodyState.error
    return StickerPanelBodyState.empty
}

fun stickerPanelEmptyText(source: StickerPanelSource): String {
    return if (source == StickerPanelSource.personal) "暂无个人表情" else "暂无房间表情"
}

fun stickerPanelReset(
    source: StickerPanelSource = StickerPanelSource.personal,
): StickerPanelLoadState {
    return StickerPanelLoadState(source = source)
}

fun stickerPanelSourceChanged(
    state: StickerPanelLoadState,
    source: StickerPanelSource,
): StickerPanelLoadState {
    return state.copyWith(source = source)
}

fun shouldLoadStickerPanel(
    state: StickerPanelLoadState,
    forceReload: Boolean,
): Boolean {
    return !state.loading && (forceReload || !state.loaded)
}

fun stickerPanelLoadStarted(state: StickerPanelLoadState): StickerPanelLoadState {
    return state.copyWith(loading = true, error = null)
}

fun stickerPanelCachedPersonalApplied(
    state: StickerPanelLoadState,
    packs: Iterable<StickerPack>,
): StickerPanelLoadState {
    return state.copyWith(personalPacks = packs.toList())
}

fun stickerPanelLoadSucceeded(
    state: StickerPanelLoadState,
    personalPacks: Iterable<StickerPack>,
    roomPacks: Iterable<StickerPack>,
): StickerPanelLoadState {
    return state.copyWith(
        personalPacks = personalPacks.toList(),
        roomPacks = roomPacks.toList(),
        loading = false,
        loaded = true,
        error = null,
    )
}

fun stickerPanelLoadFailed(
    state: StickerPanelLoadState,
    failure: Any,
): StickerPanelLoadState {
    return state.copyWith(
        loading = false,
        error = userFacingErrorMessage(failure, fallback = "加载表情包失败"),
    )
}

fun stickerPanelLoadFinished(state: StickerPanelLoadState): StickerPanelLoadState {
    return state.copyWith(loading = false)
}

fun stickerDimensionsText(
    asset: UploadedAsset,
    resolved: StickerImageDimensions? = null,
    resolving: Boolean,
    failed: Boolean,
): String {
    val width = asset.width ?: resolved?.width
    val height = asset.height ?: resolved?.height
    if (width != null && height != null) return "${width}x$height"
    if (resolving) return "正在读取尺寸"
    if (failed) return "尺寸读取失败"
    return "未知尺寸"
}
