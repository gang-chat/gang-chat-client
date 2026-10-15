import Cocoa
import FlutterMacOS
import Sparkle

@main
class AppDelegate: FlutterAppDelegate, SPUUpdaterDelegate {
  // Sparkle installs updates through its XPC installer, outside the App
  // Sandbox. It is created without starting so a build without SUPublicEDKey
  // (before update keys are set up) never shows Sparkle's configuration
  // alert; Flutter starts it and asks for checks over `gang_chat/updater`.
  lazy var updaterController = SPUStandardUpdaterController(
    startingUpdater: false,
    updaterDelegate: self,
    userDriverDelegate: nil
  )
  // Set from the app's configured release bucket so a custom bucket also
  // moves the update feed; Info.plist's SUFeedURL is the fallback.
  var updateFeedURL: String?
  private var updaterStarted = false

  override func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool {
    return true
  }

  override func applicationSupportsSecureRestorableState(_ app: NSApplication) -> Bool {
    return true
  }

  /// Starts Sparkle once, if this build carries an update-signing public key.
  /// Returns whether in-app updates are available.
  func startUpdaterIfConfigured() -> Bool {
    if updaterStarted { return true }
    let publicKey = Bundle.main.object(forInfoDictionaryKey: "SUPublicEDKey") as? String ?? ""
    guard !publicKey.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
      return false
    }
    do {
      try updaterController.updater.start()
      updaterStarted = true
      return true
    } catch {
      NSLog("Sparkle updater failed to start: \(error)")
      return false
    }
  }

  func feedURLString(for updater: SPUUpdater) -> String? {
    return updateFeedURL
  }
}
