import Cocoa
import CoreAudio
import CoreGraphics
import FlutterMacOS

private let loginWindowSize = NSSize(width: 430, height: 256)
private let resizeCursorEdgeWidth: CGFloat = 8
// Logical height of the v2 Flutter title bar. The native traffic lights are
// vertically centered within this band. Keep in sync with `_homeTitleBarHeight`
// in lib/src/v2/home_shell_title_bar.dart.
private let v2TitleBarHeight: CGFloat = 44
private let screenThumbnailChannel = "gang_chat/screen_thumbnail"
private let clipboardChannel = "gang_chat/clipboard"
private let fileDropChannel = "gang_chat/file_drop"
private let audioDevicesChannel = "gang_chat/audio_devices"
private let appBackground = NSColor(
  red: CGFloat(0x14) / 255.0,
  green: CGFloat(0x17) / 255.0,
  blue: CGFloat(0x1D) / 255.0,
  alpha: 1
)

class MainFlutterWindow: NSWindow {
  private var resizeCursorActive = false
  private var fileDropMethodChannel: FlutterMethodChannel?
  private var audioDevicesMethodChannel: FlutterMethodChannel?
  private var defaultInputListenerBlock: AudioObjectPropertyListenerBlock?
  private var defaultOutputListenerBlock: AudioObjectPropertyListenerBlock?

  override func awakeFromNib() {
    alphaValue = 0

    let flutterViewController = FlutterViewController()
    self.contentViewController = flutterViewController
    self.acceptsMouseMovedEvents = true

    titleVisibility = .hidden
    titlebarAppearsTransparent = true
    styleMask.insert(.fullSizeContentView)
    styleMask.remove(.resizable)
    backgroundColor = appBackground
    isRestorable = false
    animationBehavior = .none

    setInitialLoginFrame()

    registerScreenThumbnailChannel(flutterViewController)
    registerClipboardChannel(flutterViewController)
    registerAudioDevicesChannel(flutterViewController)
    installFileDropTarget(flutterViewController)
    RegisterGeneratedPlugins(registry: flutterViewController)

    // AppKit re-pins the traffic lights to the top edge on resize and key
    // changes, so re-center them on those events as well as once after setup.
    let center = NotificationCenter.default
    center.addObserver(
      self,
      selector: #selector(repositionTrafficLights),
      name: NSWindow.didResizeNotification,
      object: self
    )
    center.addObserver(
      self,
      selector: #selector(repositionTrafficLights),
      name: NSWindow.didBecomeKeyNotification,
      object: self
    )
    DispatchQueue.main.async { [weak self] in self?.repositionTrafficLights() }

    super.awakeFromNib()
  }

  deinit {
    NotificationCenter.default.removeObserver(self)
    removeDefaultInputListener()
    removeDefaultOutputListener()
  }

  // Centers the native traffic lights within the v2 title-bar band and insets
  // them from the left so the surrounding gap is even on all sides. No-op while
  // the buttons are hidden (e.g. the login window), so it is safe to call
  // unconditionally.
  @objc private func repositionTrafficLights() {
    let buttons: [NSButton] = [
      standardWindowButton(.closeButton),
      standardWindowButton(.miniaturizeButton),
      standardWindowButton(.zoomButton),
    ].compactMap { $0 }

    guard let titleBarView = buttons.first?.superview,
      let closeButton = buttons.first
    else { return }

    // Match the left gap to the vertical gap so the lights look evenly inset.
    let sideGap = (v2TitleBarHeight - closeButton.frame.height) / 2
    let dx = sideGap - closeButton.frame.origin.x

    for button in buttons where !button.isHidden {
      let buttonHeight = button.frame.height
      // Center the button within a band of `v2TitleBarHeight` measured from the
      // top of the title bar view. The title bar uses non-flipped coordinates
      // (y grows upward), so the top edge sits at the view's full height.
      let originY: CGFloat
      if titleBarView.isFlipped {
        originY = (v2TitleBarHeight - buttonHeight) / 2
      } else {
        originY = titleBarView.frame.height - v2TitleBarHeight / 2 - buttonHeight / 2
      }
      button.setFrameOrigin(NSPoint(x: button.frame.origin.x + dx, y: originY))
    }
  }

  override func mouseMoved(with event: NSEvent) {
    super.mouseMoved(with: event)
    updateResizeCursor(for: event.locationInWindow)
  }

  override func mouseExited(with event: NSEvent) {
    super.mouseExited(with: event)
    resetResizeCursorIfNeeded()
  }

  // window_manager's setTitleBarStyle("hidden") flips isOpaque to false. Pin
  // opacity so the window stays opaque and avoids a black strip.
  override var isOpaque: Bool {
    get { true }
    set {}
  }

  private func setInitialLoginFrame() {
    let screenFrame = NSScreen.main?.visibleFrame ?? frame
    let windowFrame = NSRect(
      x: screenFrame.origin.x + (screenFrame.width - loginWindowSize.width) / 2,
      y: screenFrame.origin.y + (screenFrame.height - loginWindowSize.height) / 2,
      width: loginWindowSize.width,
      height: loginWindowSize.height
    )
    minSize = loginWindowSize
    maxSize = loginWindowSize
    setFrame(windowFrame, display: true)
  }

  private func updateResizeCursor(for location: NSPoint) {
    guard styleMask.contains(.resizable) else {
      resetResizeCursorIfNeeded()
      return
    }

    let width = frame.width
    let height = frame.height
    let left = location.x <= resizeCursorEdgeWidth
    let right = location.x >= width - resizeCursorEdgeWidth
    let bottom = location.y <= resizeCursorEdgeWidth
    let top = location.y >= height - resizeCursorEdgeWidth

    if left || right {
      NSCursor.resizeLeftRight.set()
      resizeCursorActive = true
      return
    }

    if top || bottom {
      NSCursor.resizeUpDown.set()
      resizeCursorActive = true
      return
    }

    resetResizeCursorIfNeeded()
  }

  private func resetResizeCursorIfNeeded() {
    if !resizeCursorActive { return }
    NSCursor.arrow.set()
    resizeCursorActive = false
  }

  // The screen-share picker's thumbnails come from libwebrtc's RTCDesktopSource,
  // whose screen grabber shears frames on Retina displays (the same stride bug
  // the live share avoids by using ScreenCaptureKit). CGDisplayCreateImage lets
  // CoreGraphics own the buffer stride, so the thumbnail comes back undistorted.
  private func registerScreenThumbnailChannel(_ flutterViewController: FlutterViewController) {
    let channel = FlutterMethodChannel(
      name: screenThumbnailChannel,
      binaryMessenger: flutterViewController.engine.binaryMessenger
    )
    channel.setMethodCallHandler { call, result in
      switch call.method {
      case "captureScreenThumbnail":
        guard
          let args = call.arguments as? [String: Any],
          let displayIdString = args["displayId"] as? String,
          let displayId = CGDirectDisplayID(displayIdString)
        else {
          result(nil)
          return
        }
        let maxWidth = (args["maxWidth"] as? NSNumber)?.intValue ?? 320
        result(self.captureScreenThumbnail(displayId: displayId, maxWidth: maxWidth))
      default:
        result(FlutterMethodNotImplemented)
      }
    }
  }

  // Enumerates audio devices and reports the system defaults on
  // `gang_chat/audio_devices`.
  //
  // flutter_webrtc's CoreAudio audio device module lists zero audio devices
  // until WebRTC is actually recording (i.e. after publishing audio in a room),
  // so its enumerateDevices() comes back empty in Settings before a room is
  // joined. We sidestep that entirely by reading CoreAudio directly here.
  //
  // The deviceId we emit is the stringified CoreAudio AudioDeviceID integer
  // (e.g. "87"). That is byte-for-byte what WebRTC's macOS RTCIODevice.deviceId
  // resolves to (libwebrtc's audio_device_mac builds the guid as
  // std::to_string(AudioDeviceID)), so the ids stay compatible with
  // flutter_webrtc's selectAudioInput/selectAudioOutput once the in-room ADM is
  // populated.
  //
  // CoreAudio property listeners push default-device changes whenever the user
  // switches the default input/output in System Settings, letting the picker
  // follow them live.
  //
  // Methods:
  //   `enumerateInputs` -> [[deviceId: String, label: String, isDefault: Bool]]
  //   `enumerateOutputs` -> [[deviceId: String, label: String, isDefault: Bool]]
  //   `getDefaultInputDeviceId` -> String? (deviceId of the default mic)
  //   `getDefaultOutputDeviceId` -> String? (deviceId of the default speaker)
  //   `startListening` -> begins observing the default-device change event
  // Events:
  //   `defaultInputDeviceChanged` with argument String? (the new deviceId)
  //   `defaultOutputDeviceChanged` with argument String? (the new deviceId)
  private func registerAudioDevicesChannel(_ flutterViewController: FlutterViewController) {
    let channel = FlutterMethodChannel(
      name: audioDevicesChannel,
      binaryMessenger: flutterViewController.engine.binaryMessenger
    )
    audioDevicesMethodChannel = channel
    channel.setMethodCallHandler { [weak self] call, result in
      switch call.method {
      case "enumerateInputs":
        result(self?.enumerateInputDevices() ?? [])
      case "enumerateOutputs":
        result(self?.enumerateOutputDevices() ?? [])
      case "getDefaultInputDeviceId":
        result(self?.defaultInputDeviceId())
      case "getDefaultOutputDeviceId":
        result(self?.defaultOutputDeviceId())
      case "startListening":
        self?.installDefaultInputListener()
        self?.installDefaultOutputListener()
        result(nil)
      default:
        result(FlutterMethodNotImplemented)
      }
    }
  }

  // All CoreAudio input devices (those exposing at least one input stream), each
  // as {deviceId, label, isDefault}. deviceId is String(AudioDeviceID).
  private func enumerateInputDevices() -> [[String: Any]] {
    let defaultId = defaultInputAudioDeviceId()
    var devices: [[String: Any]] = []
    for id in allAudioDeviceIds() where deviceHasInputStreams(id) {
      let label = coreAudioStringProperty(id, kAudioDevicePropertyDeviceNameCFString)
        ?? "麦克风 \(id)"
      devices.append([
        "deviceId": String(id),
        "label": label,
        "isDefault": id == defaultId,
      ])
    }
    return devices
  }

  // All CoreAudio output devices (those exposing at least one output stream),
  // each as {deviceId, label, isDefault}. deviceId is String(AudioDeviceID).
  private func enumerateOutputDevices() -> [[String: Any]] {
    let defaultId = defaultOutputAudioDeviceId()
    var devices: [[String: Any]] = []
    for id in allAudioDeviceIds() where deviceHasOutputStreams(id) {
      let label = coreAudioStringProperty(id, kAudioDevicePropertyDeviceNameCFString)
        ?? "扬声器 \(id)"
      devices.append([
        "deviceId": String(id),
        "label": label,
        "isDefault": id == defaultId,
      ])
    }
    return devices
  }

  private func defaultInputDeviceId() -> String? {
    guard let id = defaultInputAudioDeviceId() else { return nil }
    return String(id)
  }

  private func defaultOutputDeviceId() -> String? {
    guard let id = defaultOutputAudioDeviceId() else { return nil }
    return String(id)
  }

  // The AudioDeviceID the OS currently treats as the default input, or nil.
  private func defaultInputAudioDeviceId() -> AudioDeviceID? {
    return defaultAudioDeviceId(for: kAudioHardwarePropertyDefaultInputDevice)
  }

  // The AudioDeviceID the OS currently treats as the default output, or nil.
  private func defaultOutputAudioDeviceId() -> AudioDeviceID? {
    return defaultAudioDeviceId(for: kAudioHardwarePropertyDefaultOutputDevice)
  }

  private func defaultAudioDeviceId(
    for selector: AudioObjectPropertySelector
  ) -> AudioDeviceID? {
    var address = AudioObjectPropertyAddress(
      mSelector: selector,
      mScope: kAudioObjectPropertyScopeGlobal,
      mElement: kAudioObjectPropertyElementMain
    )
    var deviceId = AudioDeviceID(0)
    var size = UInt32(MemoryLayout<AudioDeviceID>.size)
    let status = AudioObjectGetPropertyData(
      AudioObjectID(kAudioObjectSystemObject),
      &address,
      0,
      nil,
      &size,
      &deviceId
    )
    if status != noErr || deviceId == kAudioObjectUnknown { return nil }
    return deviceId
  }

  private func allAudioDeviceIds() -> [AudioDeviceID] {
    var address = AudioObjectPropertyAddress(
      mSelector: kAudioHardwarePropertyDevices,
      mScope: kAudioObjectPropertyScopeGlobal,
      mElement: kAudioObjectPropertyElementMain
    )
    var size = UInt32(0)
    guard
      AudioObjectGetPropertyDataSize(
        AudioObjectID(kAudioObjectSystemObject), &address, 0, nil, &size
      ) == noErr
    else { return [] }
    let count = Int(size) / MemoryLayout<AudioDeviceID>.size
    guard count > 0 else { return [] }
    var ids = [AudioDeviceID](repeating: 0, count: count)
    guard
      AudioObjectGetPropertyData(
        AudioObjectID(kAudioObjectSystemObject), &address, 0, nil, &size, &ids
      ) == noErr
    else { return [] }
    return ids
  }

  // True when the device exposes at least one input stream (so it can be a mic).
  private func deviceHasInputStreams(_ device: AudioDeviceID) -> Bool {
    return deviceHasStreams(device, scope: kAudioObjectPropertyScopeInput)
  }

  // True when the device exposes at least one output stream (so it can play).
  private func deviceHasOutputStreams(_ device: AudioDeviceID) -> Bool {
    return deviceHasStreams(device, scope: kAudioObjectPropertyScopeOutput)
  }

  private func deviceHasStreams(
    _ device: AudioDeviceID,
    scope: AudioObjectPropertyScope
  ) -> Bool {
    var address = AudioObjectPropertyAddress(
      mSelector: kAudioDevicePropertyStreams,
      mScope: scope,
      mElement: kAudioObjectPropertyElementMain
    )
    var size = UInt32(0)
    guard
      AudioObjectGetPropertyDataSize(device, &address, 0, nil, &size) == noErr
    else { return false }
    return size > 0
  }

  private func coreAudioStringProperty(
    _ device: AudioDeviceID,
    _ selector: AudioObjectPropertySelector
  ) -> String? {
    var address = AudioObjectPropertyAddress(
      mSelector: selector,
      mScope: kAudioObjectPropertyScopeGlobal,
      mElement: kAudioObjectPropertyElementMain
    )
    var value: CFString = "" as CFString
    var size = UInt32(MemoryLayout<CFString>.size)
    let status = withUnsafeMutablePointer(to: &value) {
      AudioObjectGetPropertyData(device, &address, 0, nil, &size, $0)
    }
    if status != noErr { return nil }
    let string = value as String
    return string.isEmpty ? nil : string
  }


  private func installDefaultInputListener() {
    guard defaultInputListenerBlock == nil else { return }
    var address = AudioObjectPropertyAddress(
      mSelector: kAudioHardwarePropertyDefaultInputDevice,
      mScope: kAudioObjectPropertyScopeGlobal,
      mElement: kAudioObjectPropertyElementMain
    )
    let block: AudioObjectPropertyListenerBlock = { [weak self] _, _ in
      guard let self = self else { return }
      self.audioDevicesMethodChannel?.invokeMethod(
        "defaultInputDeviceChanged",
        arguments: self.defaultInputDeviceId()
      )
    }
    let status = AudioObjectAddPropertyListenerBlock(
      AudioObjectID(kAudioObjectSystemObject),
      &address,
      DispatchQueue.main,
      block
    )
    if status == noErr {
      defaultInputListenerBlock = block
    }
  }

  private func removeDefaultInputListener() {
    guard let block = defaultInputListenerBlock else { return }
    var address = AudioObjectPropertyAddress(
      mSelector: kAudioHardwarePropertyDefaultInputDevice,
      mScope: kAudioObjectPropertyScopeGlobal,
      mElement: kAudioObjectPropertyElementMain
    )
    AudioObjectRemovePropertyListenerBlock(
      AudioObjectID(kAudioObjectSystemObject),
      &address,
      DispatchQueue.main,
      block
    )
    defaultInputListenerBlock = nil
  }

  private func installDefaultOutputListener() {
    guard defaultOutputListenerBlock == nil else { return }
    var address = AudioObjectPropertyAddress(
      mSelector: kAudioHardwarePropertyDefaultOutputDevice,
      mScope: kAudioObjectPropertyScopeGlobal,
      mElement: kAudioObjectPropertyElementMain
    )
    let block: AudioObjectPropertyListenerBlock = { [weak self] _, _ in
      guard let self = self else { return }
      self.audioDevicesMethodChannel?.invokeMethod(
        "defaultOutputDeviceChanged",
        arguments: self.defaultOutputDeviceId()
      )
    }
    let status = AudioObjectAddPropertyListenerBlock(
      AudioObjectID(kAudioObjectSystemObject),
      &address,
      DispatchQueue.main,
      block
    )
    if status == noErr {
      defaultOutputListenerBlock = block
    }
  }

  private func removeDefaultOutputListener() {
    guard let block = defaultOutputListenerBlock else { return }
    var address = AudioObjectPropertyAddress(
      mSelector: kAudioHardwarePropertyDefaultOutputDevice,
      mScope: kAudioObjectPropertyScopeGlobal,
      mElement: kAudioObjectPropertyElementMain
    )
    AudioObjectRemovePropertyListenerBlock(
      AudioObjectID(kAudioObjectSystemObject),
      &address,
      DispatchQueue.main,
      block
    )
    defaultOutputListenerBlock = nil
  }

  // Mirrors the Windows runner's `gang_chat/clipboard` channel so paste-to-send
  // works on macOS: `readFilePaths` returns paths of files copied in Finder,
  // `readImageFile` returns a screenshot/copied image re-encoded as PNG.
  private func registerClipboardChannel(_ flutterViewController: FlutterViewController) {
    let channel = FlutterMethodChannel(
      name: clipboardChannel,
      binaryMessenger: flutterViewController.engine.binaryMessenger
    )
    channel.setMethodCallHandler { call, result in
      switch call.method {
      case "readFilePaths":
        result(self.readClipboardFilePaths())
      case "readImageFile":
        result(self.readClipboardImageFile())
      case "writeFilePaths":
        result(self.writeClipboardFilePaths(call.arguments))
      case "writeImageFile":
        result(self.writeClipboardImageFile(call.arguments))
      default:
        result(FlutterMethodNotImplemented)
      }
    }
  }

  private func readClipboardFilePaths() -> [String] {
    let pasteboard = NSPasteboard.general
    guard
      let urls = pasteboard.readObjects(
        forClasses: [NSURL.self],
        options: [.urlReadingFileURLsOnly: true]
      ) as? [URL]
    else {
      return []
    }
    return urls.filter { $0.isFileURL }.map { $0.path }
  }

  private func writeClipboardFilePaths(_ arguments: Any?) -> Bool {
    guard
      let args = arguments as? [String: Any],
      let paths = args["paths"] as? [String]
    else {
      return false
    }
    let urls = paths
      .map { URL(fileURLWithPath: $0) }
      .filter { FileManager.default.fileExists(atPath: $0.path) }
    guard !urls.isEmpty else { return false }

    let pasteboard = NSPasteboard.general
    pasteboard.clearContents()
    return pasteboard.writeObjects(urls as [NSURL])
  }

  private func readClipboardImageFile() -> [String: Any]? {
    let pasteboard = NSPasteboard.general

    // Prefer a PNG/TIFF blob already on the pasteboard; fall back to decoding
    // any NSImage representation (e.g. a screenshot) and re-encoding to PNG so
    // the Dart side always receives `image/png` like the Windows runner.
    var pngData: Data?
    if let png = pasteboard.data(forType: .png) {
      pngData = png
    } else if let tiff = pasteboard.data(forType: .tiff),
      let rep = NSBitmapImageRep(data: tiff) {
      pngData = rep.representation(using: .png, properties: [:])
    } else if let images = pasteboard.readObjects(forClasses: [NSImage.self]) as? [NSImage],
      let image = images.first,
      let tiff = image.tiffRepresentation,
      let rep = NSBitmapImageRep(data: tiff) {
      pngData = rep.representation(using: .png, properties: [:])
    }

    guard let data = pngData, !data.isEmpty else { return nil }
    return [
      "bytes": FlutterStandardTypedData(bytes: data),
      "filename": "clipboard-image.png",
      "mime_type": "image/png",
    ]
  }

  // Writes raw image bytes onto the general pasteboard so they can be pasted
  // into other apps. The bytes (any format NSImage can decode, typically PNG)
  // are normalized to both PNG and TIFF representations to maximize the set of
  // apps that accept the paste. Returns false on malformed input.
  private func writeClipboardImageFile(_ arguments: Any?) -> Bool {
    guard
      let args = arguments as? [String: Any],
      let typed = args["bytes"] as? FlutterStandardTypedData
    else {
      return false
    }
    let data = typed.data
    guard !data.isEmpty, let image = NSImage(data: data) else { return false }

    let pasteboard = NSPasteboard.general
    pasteboard.clearContents()

    // Writing the NSImage covers most consumers; also attach an explicit PNG
    // representation for apps that look for it specifically.
    var wrote = pasteboard.writeObjects([image])
    if let tiff = image.tiffRepresentation,
      let rep = NSBitmapImageRep(data: tiff),
      let png = rep.representation(using: .png, properties: [:]) {
      pasteboard.setData(png, forType: .png)
      wrote = true
    }
    return wrote
  }

  // Adds a transparent overlay over the Flutter view that accepts file drags
  // and forwards them on `gang_chat/file_drop` as `dropFiles {paths,x,y}`,
  // matching the Windows runner. The overlay is flipped (origin top-left) so
  // its drop coordinates already line up with Flutter's logical coordinate
  // space, and it passes hit-testing through so it never steals mouse events.
  private func installFileDropTarget(_ flutterViewController: FlutterViewController) {
    let channel = FlutterMethodChannel(
      name: fileDropChannel,
      binaryMessenger: flutterViewController.engine.binaryMessenger
    )
    fileDropMethodChannel = channel

    let contentView = flutterViewController.view
    let dropView = FileDropView()
    dropView.onDrop = { [weak self] paths, point in
      guard let self = self, !paths.isEmpty else { return }
      self.fileDropMethodChannel?.invokeMethod(
        "dropFiles",
        arguments: [
          "paths": paths,
          "x": Double(point.x),
          "y": Double(point.y),
        ]
      )
    }
    dropView.translatesAutoresizingMaskIntoConstraints = false
    contentView.addSubview(dropView)
    NSLayoutConstraint.activate([
      dropView.leadingAnchor.constraint(equalTo: contentView.leadingAnchor),
      dropView.trailingAnchor.constraint(equalTo: contentView.trailingAnchor),
      dropView.topAnchor.constraint(equalTo: contentView.topAnchor),
      dropView.bottomAnchor.constraint(equalTo: contentView.bottomAnchor),
    ])
  }

  private func captureScreenThumbnail(displayId: CGDirectDisplayID, maxWidth: Int) -> FlutterStandardTypedData? {
    guard let cgImage = CGDisplayCreateImage(displayId) else { return nil }

    let sourceWidth = cgImage.width
    let sourceHeight = cgImage.height
    guard sourceWidth > 0, sourceHeight > 0 else { return nil }

    let targetWidth = min(maxWidth, sourceWidth)
    let scale = Double(targetWidth) / Double(sourceWidth)
    let targetHeight = max(1, Int((Double(sourceHeight) * scale).rounded()))

    let colorSpace = CGColorSpaceCreateDeviceRGB()
    guard
      let context = CGContext(
        data: nil,
        width: targetWidth,
        height: targetHeight,
        bitsPerComponent: 8,
        bytesPerRow: 0,
        space: colorSpace,
        bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
      )
    else { return nil }

    context.interpolationQuality = .medium
    context.draw(cgImage, in: CGRect(x: 0, y: 0, width: targetWidth, height: targetHeight))

    guard let scaledImage = context.makeImage() else { return nil }

    let bitmapRep = NSBitmapImageRep(cgImage: scaledImage)
    guard let pngData = bitmapRep.representation(using: .png, properties: [:]) else {
      return nil
    }
    return FlutterStandardTypedData(bytes: pngData)
  }
}

/// Transparent drag-and-drop overlay for the Flutter view. Accepts file drags
/// and reports dropped paths plus the drop point in Flutter logical coordinates
/// (origin top-left). `isFlipped` keeps the coordinate system aligned with
/// Flutter, and `hitTest` returns nil so the view never intercepts clicks.
final class FileDropView: NSView {
  var onDrop: (([String], CGPoint) -> Void)?

  override init(frame frameRect: NSRect) {
    super.init(frame: frameRect)
    registerForDraggedTypes([.fileURL])
  }

  required init?(coder: NSCoder) {
    super.init(coder: coder)
    registerForDraggedTypes([.fileURL])
  }

  override var isFlipped: Bool { true }

  // Pass all mouse events through to the Flutter view beneath us; this view
  // only exists to receive drags.
  override func hitTest(_ point: NSPoint) -> NSView? { nil }

  override func draggingEntered(_ sender: NSDraggingInfo) -> NSDragOperation {
    return hasFileURLs(sender) ? .copy : []
  }

  override func draggingUpdated(_ sender: NSDraggingInfo) -> NSDragOperation {
    return hasFileURLs(sender) ? .copy : []
  }

  override func prepareForDragOperation(_ sender: NSDraggingInfo) -> Bool {
    return hasFileURLs(sender)
  }

  override func performDragOperation(_ sender: NSDraggingInfo) -> Bool {
    let paths = filePaths(sender)
    guard !paths.isEmpty else { return false }
    let point = convert(sender.draggingLocation, from: nil)
    onDrop?(paths, point)
    return true
  }

  private func hasFileURLs(_ sender: NSDraggingInfo) -> Bool {
    return !filePaths(sender).isEmpty
  }

  private func filePaths(_ sender: NSDraggingInfo) -> [String] {
    guard
      let urls = sender.draggingPasteboard.readObjects(
        forClasses: [NSURL.self],
        options: [.urlReadingFileURLsOnly: true]
      ) as? [URL]
    else {
      return []
    }
    return urls.filter { $0.isFileURL }.map { $0.path }
  }
}
