import 'package:flutter/widgets.dart';

import '../config/app_config.dart';

/// Inherited widget that exposes the active [AppConfig] to descendants.
class AppConfigScope extends InheritedWidget {
  const AppConfigScope({super.key, required this.config, required super.child});

  final AppConfig config;

  static AppConfig of(BuildContext context) {
    final scope = context.dependOnInheritedWidgetOfExactType<AppConfigScope>();
    return scope?.config ?? const AppConfig.defaults();
  }

  @override
  bool updateShouldNotify(AppConfigScope oldWidget) =>
      oldWidget.config.apiBaseUrl != config.apiBaseUrl ||
      oldWidget.config.assetBaseUrl != config.assetBaseUrl ||
      oldWidget.config.releaseBucketUrl != config.releaseBucketUrl;
}
