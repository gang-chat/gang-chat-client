// Checks that a Sparkle EdDSA signature over an update archive verifies
// against the public key the app ships with (SUPublicEDKey in Info.plist).
//
// A mismatch means installed apps would reject the update, so the release
// workflow fails instead of publishing it.
//
// Usage:
//   swift tool/verify_sparkle_signature.swift <SUPublicEDKey> <edSignature> <archive>
import CryptoKit
import Foundation

let arguments = CommandLine.arguments
guard arguments.count == 4 else {
  FileHandle.standardError.write(
    "usage: verify_sparkle_signature.swift <public-key> <signature> <archive>\n"
      .data(using: .utf8)!)
  exit(2)
}

guard
  let publicKeyData = Data(base64Encoded: arguments[1]),
  let signature = Data(base64Encoded: arguments[2])
else {
  FileHandle.standardError.write("public key or signature is not base64\n".data(using: .utf8)!)
  exit(2)
}

do {
  let publicKey = try Curve25519.Signing.PublicKey(rawRepresentation: publicKeyData)
  let archive = try Data(contentsOf: URL(fileURLWithPath: arguments[3]), options: .mappedIfSafe)
  if publicKey.isValidSignature(signature, for: archive) {
    print("Sparkle signature matches SUPublicEDKey")
    exit(0)
  }
  FileHandle.standardError.write(
    "Sparkle signature does not match SUPublicEDKey\n".data(using: .utf8)!)
  exit(1)
} catch {
  FileHandle.standardError.write("\(error)\n".data(using: .utf8)!)
  exit(2)
}
