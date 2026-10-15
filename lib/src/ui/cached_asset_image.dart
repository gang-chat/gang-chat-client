import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/material.dart';

import '../app/media_cache_controller.dart';
import 'media_cache_scope.dart';
import 'tokens.dart';

typedef CachedAssetImageErrorBuilder =
    Widget Function(BuildContext context, Object error, StackTrace? stackTrace);

/// Loads [request] through [cache] as an image: from the on-disk cache on
/// native platforms, and from memory on the web, which has no file system.
Future<ImageProvider> cachedMediaImageProvider(
  MediaCacheController cache,
  MediaCacheRequest request,
) async {
  if (kIsWeb) {
    return MemoryImage(await cache.getOrDownloadBytes(request: request));
  }
  return FileImage(await cache.getOrDownload(request: request));
}

class CachedAssetImage extends StatefulWidget {
  const CachedAssetImage({
    super.key,
    required this.url,
    this.filename,
    this.mimeType,
    this.expectedBytes,
    this.namespace = 'asset',
    this.cache,
    this.width,
    this.height,
    this.fit,
    this.loadingBuilder,
    this.errorBuilder,
  });

  final String url;
  final String? filename;
  final String? mimeType;
  final int? expectedBytes;
  final String namespace;
  final MediaCacheController? cache;
  final double? width;
  final double? height;
  final BoxFit? fit;
  final WidgetBuilder? loadingBuilder;
  final CachedAssetImageErrorBuilder? errorBuilder;

  @override
  State<CachedAssetImage> createState() => _CachedAssetImageState();
}

class _CachedAssetImageState extends State<CachedAssetImage> {
  Future<ImageProvider>? _image;
  MediaCacheController? _scopeCache;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final scopeCache = MediaCacheScope.of(context);
    if (!identical(_scopeCache, scopeCache)) {
      _scopeCache = scopeCache;
      _image = _load();
    }
  }

  @override
  void didUpdateWidget(CachedAssetImage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.url != widget.url ||
        oldWidget.filename != widget.filename ||
        oldWidget.mimeType != widget.mimeType ||
        oldWidget.expectedBytes != widget.expectedBytes ||
        oldWidget.namespace != widget.namespace ||
        !identical(oldWidget.cache, widget.cache)) {
      if (_scopeCache != null) _image = _load();
    }
  }

  Future<ImageProvider> _load() {
    final request = MediaCacheRequest.tryFromUrl(
      url: widget.url,
      filename: widget.filename,
      mimeType: widget.mimeType,
      expectedBytes: widget.expectedBytes,
      namespace: widget.namespace,
    );
    if (request == null) {
      return Future<ImageProvider>.error(StateError('图片地址无效'));
    }
    final cache = widget.cache ?? _scopeCache ?? MediaCacheScope.of(context);
    return cachedMediaImageProvider(cache, request);
  }

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<ImageProvider>(
      future: _image,
      builder: (context, snapshot) {
        final image = snapshot.data;
        if (image != null) {
          return Image(
            image: image,
            width: widget.width,
            height: widget.height,
            fit: widget.fit,
            gaplessPlayback: true,
          );
        }
        final error = snapshot.error;
        if (error != null) {
          return widget.errorBuilder?.call(
                context,
                error,
                snapshot.stackTrace,
              ) ??
              _DefaultCachedAssetImageError(
                width: widget.width,
                height: widget.height,
              );
        }
        return widget.loadingBuilder?.call(context) ??
            _DefaultCachedAssetImageLoading(
              width: widget.width,
              height: widget.height,
            );
      },
    );
  }
}

class _DefaultCachedAssetImageLoading extends StatelessWidget {
  const _DefaultCachedAssetImageLoading({this.width, this.height});

  final double? width;
  final double? height;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: width,
      height: height,
      child: const Center(
        child: SizedBox.square(
          dimension: 18,
          child: CircularProgressIndicator(
            strokeWidth: 2,
            color: UiColors.textSecondary,
          ),
        ),
      ),
    );
  }
}

class _DefaultCachedAssetImageError extends StatelessWidget {
  const _DefaultCachedAssetImageError({this.width, this.height});

  final double? width;
  final double? height;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: width,
      height: height,
      child: const Center(
        child: Icon(
          Icons.broken_image_outlined,
          color: UiColors.textMuted,
          size: 24,
        ),
      ),
    );
  }
}
