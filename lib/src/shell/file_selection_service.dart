import 'dart:typed_data';

import 'package:file_selector/file_selector.dart' as file_selector;

import 'android_system_service.dart';

class FileTypeGroup {
  const FileTypeGroup({required this.label, required this.extensions});

  final String label;
  final List<String> extensions;
}

class SelectedFile {
  const SelectedFile._({
    required this.name,
    required this.mimeType,
    required Future<Uint8List> Function() readAsBytes,
    required Future<int> Function() length,
  }) : _readAsBytes = readAsBytes,
       _length = length;

  factory SelectedFile._fromSelectorFile(file_selector.XFile file) {
    return SelectedFile._(
      name: file.name,
      mimeType: file.mimeType,
      readAsBytes: file.readAsBytes,
      length: file.length,
    );
  }

  factory SelectedFile.fromPath(String path) {
    return SelectedFile._fromSelectorFile(file_selector.XFile(path));
  }

  factory SelectedFile.fromBytes({
    required String name,
    required String mimeType,
    required Uint8List bytes,
  }) {
    final data = Uint8List.fromList(bytes);
    return SelectedFile._(
      name: name,
      mimeType: mimeType,
      readAsBytes: () async => Uint8List.fromList(data),
      length: () async => data.length,
    );
  }

  final String name;
  final String? mimeType;
  final Future<Uint8List> Function() _readAsBytes;
  final Future<int> Function() _length;

  Future<Uint8List> readAsBytes() => _readAsBytes();

  Future<int> length() => _length();
}

class SaveFileLocation {
  const SaveFileLocation({required this.path, this.documentUri});

  final String path;
  final String? documentUri;

  bool get requiresCommit => documentUri != null;
}

class FileSelectionService {
  const FileSelectionService({
    this.androidSystemService = const AndroidSystemService(),
  });

  final AndroidSystemService androidSystemService;

  Future<SelectedFile?> openFile({
    List<FileTypeGroup> acceptedTypeGroups = const [],
  }) async {
    final file = acceptedTypeGroups.isEmpty
        ? await file_selector.openFile()
        : await file_selector.openFile(
            acceptedTypeGroups: _selectorTypeGroups(acceptedTypeGroups),
          );
    return file == null ? null : SelectedFile._fromSelectorFile(file);
  }

  Future<List<SelectedFile>> openFiles({
    List<FileTypeGroup> acceptedTypeGroups = const [],
  }) async {
    final files = acceptedTypeGroups.isEmpty
        ? await file_selector.openFiles()
        : await file_selector.openFiles(
            acceptedTypeGroups: _selectorTypeGroups(acceptedTypeGroups),
          );
    return files.map(SelectedFile._fromSelectorFile).toList(growable: false);
  }

  Future<SaveFileLocation?> getSaveLocation({
    required String suggestedName,
    List<FileTypeGroup> acceptedTypeGroups = const [],
    String? confirmButtonText,
  }) async {
    if (androidSystemService.isSupported) {
      final location = await androidSystemService.createDocument(
        suggestedName: suggestedName,
        mimeType: _mimeTypeForSuggestedName(suggestedName),
      );
      return location == null
          ? null
          : SaveFileLocation(
              path: location.stagingPath,
              documentUri: location.uri,
            );
    }
    final location = acceptedTypeGroups.isEmpty
        ? await file_selector.getSaveLocation(
            suggestedName: suggestedName,
            confirmButtonText: confirmButtonText,
          )
        : await file_selector.getSaveLocation(
            suggestedName: suggestedName,
            acceptedTypeGroups: _selectorTypeGroups(acceptedTypeGroups),
            confirmButtonText: confirmButtonText,
          );
    return location == null ? null : SaveFileLocation(path: location.path);
  }

  List<SelectedFile> filesFromPaths(Iterable<String> paths) {
    return paths.map(SelectedFile.fromPath).toList(growable: false);
  }

  Future<void> saveBytesToPath({
    required Uint8List bytes,
    required String path,
    required String filename,
    String? mimeType,
  }) {
    return file_selector.XFile.fromData(
      bytes,
      mimeType: mimeType,
      name: filename,
    ).saveTo(path);
  }

  Future<void> saveBytesToLocation({
    required Uint8List bytes,
    required SaveFileLocation location,
    required String filename,
    String? mimeType,
  }) async {
    final documentUri = location.documentUri;
    if (documentUri != null) {
      await androidSystemService.writeDocumentBytes(
        uri: documentUri,
        bytes: bytes,
      );
      return;
    }
    await saveBytesToPath(
      bytes: bytes,
      path: location.path,
      filename: filename,
      mimeType: mimeType,
    );
  }

  Future<void> commitLocation(SaveFileLocation location) async {
    final documentUri = location.documentUri;
    if (documentUri == null) return;
    await androidSystemService.commitDocumentFile(
      uri: documentUri,
      stagingPath: location.path,
    );
  }

  Future<void> discardLocation(SaveFileLocation location) async {
    if (!location.requiresCommit) return;
    await androidSystemService.discardDocumentFile(location.path);
  }

  Future<String?> saveBytesToDownloads({
    required Uint8List bytes,
    required String filename,
    String? mimeType,
  }) {
    return androidSystemService.saveToDownloads(
      filename: filename,
      bytes: bytes,
      mimeType: mimeType ?? _mimeTypeForSuggestedName(filename),
    );
  }
}

List<file_selector.XTypeGroup> _selectorTypeGroups(List<FileTypeGroup> groups) {
  return groups
      .map(
        (group) => file_selector.XTypeGroup(
          label: group.label,
          extensions: group.extensions,
        ),
      )
      .toList(growable: false);
}

String _mimeTypeForSuggestedName(String filename) {
  final extension = filename.split('.').last.toLowerCase();
  return switch (extension) {
    'png' => 'image/png',
    'jpg' || 'jpeg' => 'image/jpeg',
    'webp' => 'image/webp',
    'gif' => 'image/gif',
    'zip' => 'application/zip',
    'mp3' => 'audio/mpeg',
    'm4a' => 'audio/mp4',
    'wav' => 'audio/wav',
    'mp4' => 'video/mp4',
    'pdf' => 'application/pdf',
    'txt' => 'text/plain',
    _ => 'application/octet-stream',
  };
}
