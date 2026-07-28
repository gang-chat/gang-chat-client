#ifndef FLUTTER_WEBRTC_BASE_HXX
#define FLUTTER_WEBRTC_BASE_HXX

#include "flutter_common.h"

#include <string.h>
#include <atomic>
#include <functional>
#include <list>
#include <map>
#include <memory>
#include <mutex>

#include "libwebrtc.h"

#include "rtc_audio_device.h"
#include "rtc_audio_processing.h"
#include "rtc_desktop_device.h"
#include "rtc_dtmf_sender.h"
#include "rtc_frame_cryptor.h"
#include "rtc_media_stream.h"
#include "rtc_media_track.h"
#include "rtc_mediaconstraints.h"
#include "rtc_peerconnection.h"
#include "rtc_peerconnection_factory.h"
#include "rtc_video_device.h"

namespace flutter_webrtc_plugin {

using namespace libwebrtc;

class FlutterVideoRenderer;
class FlutterRTCDataChannelObserver;
class FlutterPeerConnectionObserver;
class ScreenAudioCapture;

class LocalAudioInputVolumeProcessor
    : public RTCAudioProcessing::CustomProcessing {
 public:
  void set_volume(double volume);

  void Initialize(int sample_rate_hz, int num_channels) override;
  void Process(int num_bands,
               int num_frames,
               int buffer_size,
               float* buffer) override;
  void Reset(int new_rate) override {}
  void Release() override {}

 private:
  std::atomic<double> volume_{1.0};
  std::atomic<int> channels_{1};
};

class FlutterWebRTCBase {
 public:
  friend class FlutterMediaStream;
  friend class FlutterPeerConnection;
  friend class FlutterVideoRendererManager;
  friend class FlutterDataChannel;
  friend class FlutterPeerConnectionObserver;
  friend class FlutterScreenCapture;
  friend class FlutterFrameCryptor;
  friend class FlutterDataPacketCryptor;
  enum ParseConstraintType { kMandatory, kOptional };

 public:
  FlutterWebRTCBase(BinaryMessenger* messenger,
                    TextureRegistrar* textures,
                    TaskRunner* task_runner);
  ~FlutterWebRTCBase();

  virtual scoped_refptr<RTCAudioProcessing> audio_processing() {
    return audio_processing_;
  }

  virtual scoped_refptr<RTCMediaTrack> MediaTrackForId(const std::string& id);

  void SetLocalAudioInputVolume(double volume);

  std::string GenerateUUID();

  RTCPeerConnection* PeerConnectionForId(const std::string& id);

  void RemovePeerConnectionForId(const std::string& id);

  void RemoveMediaTrackForId(const std::string& id);

  FlutterPeerConnectionObserver* PeerConnectionObserversForId(
      const std::string& id);

  void RemovePeerConnectionObserversForId(const std::string& id);

  scoped_refptr<RTCMediaStream> MediaStreamForId(
      const std::string& id,
      std::string ownerTag = std::string());

  void RemoveStreamForId(const std::string& id);

  bool ParseConstraints(const EncodableMap& constraints,
                        RTCConfiguration* configuration);

  scoped_refptr<RTCMediaConstraints> ParseMediaConstraints(
      const EncodableMap& constraints);

  bool ParseRTCConfiguration(const EncodableMap& map,
                             RTCConfiguration& configuration);

  scoped_refptr<RTCMediaTrack> MediaTracksForId(const std::string& id);

  void RemoveTracksForId(const std::string& id);

  void RegisterLocalTrackCleanup(const std::string& id,
                                 std::function<void()> cleanup);

  void RunLocalTrackCleanup(const std::string& id);

#if defined(_WIN32)
  scoped_refptr<RTCPeerConnectionFactory> ScreenAudioFactory();

  void ConfigureScreenAudioCapture(bool requested,
                                   unsigned long target_process_id,
                                   bool include_process_tree,
                                   bool allow_system_loopback_fallback);

  std::shared_ptr<ScreenAudioCapture> StartScreenAudioCapture(
      scoped_refptr<RTCAudioSource> source);

  void StopScreenAudioCapture();
#endif

  EventChannelProxy* event_channel();

  libwebrtc::scoped_refptr<libwebrtc::RTCRtpSender> GetRtpSenderById(
      RTCPeerConnection* pc,
      std::string id);

  libwebrtc::scoped_refptr<libwebrtc::RTCRtpReceiver> GetRtpReceiverById(
      RTCPeerConnection* pc,
      std::string id);

  libwebrtc::scoped_refptr<libwebrtc::KeyProvider> GetKeyProviderForId(
      const std::string& keyProviderId);

 private:
  void PreserveRuntimeForAbandonedNativeWork() {
    preserve_runtime_for_abandoned_native_work_ = true;
  }

  void ParseConstraints(const EncodableMap& src,
                        scoped_refptr<RTCMediaConstraints> mediaConstraints,
                        ParseConstraintType type = kMandatory);

  bool CreateIceServers(const EncodableList& iceServersArray,
                        IceServer* ice_servers);

 protected:
  scoped_refptr<RTCPeerConnectionFactory> factory_;
  scoped_refptr<RTCAudioDevice> audio_device_;
  scoped_refptr<RTCVideoDevice> video_device_;
  scoped_refptr<RTCDesktopDevice> desktop_device_;
  scoped_refptr<RTCAudioProcessing> audio_processing_;
#if defined(_WIN32)
  scoped_refptr<RTCPeerConnectionFactory> screen_audio_factory_;
  std::shared_ptr<ScreenAudioCapture> screen_audio_capture_;
  bool screen_audio_capture_requested_ = false;
  unsigned long screen_audio_target_process_id_ = 0;
  bool screen_audio_include_process_tree_ = false;
  bool screen_audio_allow_system_loopback_fallback_ = false;
#endif
  std::unique_ptr<LocalAudioInputVolumeProcessor>
      local_audio_input_volume_processor_;
  RTCConfiguration configuration_;

  std::map<std::string, scoped_refptr<libwebrtc::KeyProvider>> key_providers_;
  std::map<std::string, scoped_refptr<RTCPeerConnection>> peerconnections_;
  std::map<std::string, scoped_refptr<RTCMediaStream>> local_streams_;
  std::map<std::string, scoped_refptr<RTCMediaTrack>> local_tracks_;
  std::map<std::string, scoped_refptr<RTCVideoCapturer>> video_capturers_;
  std::map<int64_t, std::shared_ptr<FlutterVideoRenderer>> renders_;
  std::map<std::string, std::function<void()>> local_track_cleanups_;
  std::map<std::string, std::shared_ptr<FlutterRTCDataChannelObserver>>
      data_channel_observers_;
  std::map<std::string, std::shared_ptr<FlutterPeerConnectionObserver>>
      peerconnection_observers_;
  mutable std::mutex mutex_;
  bool preserve_runtime_for_abandoned_native_work_ = false;

  void lock() { mutex_.lock(); }
  void unlock() { mutex_.unlock(); }

 protected:
  BinaryMessenger* messenger_;
  TaskRunner* task_runner_;
  TextureRegistrar* textures_;
  std::unique_ptr<EventChannelProxy> event_channel_;
};

}  // namespace flutter_webrtc_plugin

#endif  // !FLUTTER_WEBRTC_BASE_HXX
