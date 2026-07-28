#include "flutter_media_stream.h"
#include "task_runner.h"
#include "timed_serial_worker.h"

#include <cstdint>
#include <map>
#include <mutex>
#include <set>
#include <stdexcept>
#include <vector>

#if defined(_WIN32)
#ifndef NOMINMAX
#define NOMINMAX
#endif
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>

#include <propkeydef.h>
#include <functiondiscoverykeys_devpkey.h>
#include <mmdeviceapi.h>
#include <propsys.h>
#endif

#define DEFAULT_WIDTH 1280
#define DEFAULT_HEIGHT 720
#define DEFAULT_FPS 30

namespace {

std::string AudioDeviceIdFromNameAndGuid(const char* name, const char* guid) {
  return strlen(guid) > 0 ? std::string(guid) : std::string(name);
}

void AppendAudioDevice(EncodableList& sources,
                       std::set<std::string>& seen,
                       const std::string& kind,
                       const std::string& label,
                       const std::string& device_id,
                       const std::string& group_id = {}) {
  if (device_id.empty()) {
    return;
  }
  const std::string key = kind + ":" + device_id;
  if (!seen.insert(key).second) {
    return;
  }
  EncodableMap audio;
  audio[EncodableValue("label")] = EncodableValue(label);
  audio[EncodableValue("deviceId")] = EncodableValue(device_id);
  audio[EncodableValue("groupId")] =
      EncodableValue(group_id.empty() ? device_id : group_id);
  audio[EncodableValue("facing")] = "";
  audio[EncodableValue("kind")] = kind;
  sources.push_back(EncodableValue(audio));
}

#if defined(_WIN32)
template <typename T>
void SafeRelease(T*& value) {
  if (value) {
    value->Release();
    value = nullptr;
  }
}

class ScopedComInitialization {
 public:
  ScopedComInitialization() {
    const HRESULT result = CoInitializeEx(nullptr, COINIT_APARTMENTTHREADED);
    initialized_here_ = SUCCEEDED(result);
    ok_ = initialized_here_ || result == RPC_E_CHANGED_MODE;
  }

  ~ScopedComInitialization() {
    if (initialized_here_) {
      CoUninitialize();
    }
  }

  bool ok() const { return ok_; }

 private:
  bool ok_ = false;
  bool initialized_here_ = false;
};

std::string WideToUtf8(const wchar_t* value) {
  if (!value || value[0] == L'\0') {
    return {};
  }
  const int size = WideCharToMultiByte(CP_UTF8, 0, value, -1, nullptr, 0,
                                       nullptr, nullptr);
  if (size <= 1) {
    return {};
  }
  std::string result(size, '\0');
  WideCharToMultiByte(CP_UTF8, 0, value, -1, result.data(), size, nullptr,
                      nullptr);
  result.pop_back();
  return result;
}

std::string AudioEndpointId(IMMDevice* device) {
  if (!device) {
    return {};
  }
  wchar_t* raw_id = nullptr;
  if (FAILED(device->GetId(&raw_id)) || !raw_id) {
    return {};
  }
  std::string device_id = WideToUtf8(raw_id);
  CoTaskMemFree(raw_id);
  return device_id;
}

std::string AudioEndpointFriendlyName(IMMDevice* device,
                                      const std::string& fallback) {
  if (!device) {
    return fallback;
  }
  IPropertyStore* properties = nullptr;
  PROPVARIANT name;
  PropVariantInit(&name);
  std::string label;
  if (SUCCEEDED(device->OpenPropertyStore(STGM_READ, &properties)) &&
      SUCCEEDED(properties->GetValue(PKEY_Device_FriendlyName, &name)) &&
      name.vt == VT_LPWSTR) {
    label = WideToUtf8(name.pwszVal);
  }
  PropVariantClear(&name);
  SafeRelease(properties);
  return label.empty() ? fallback : label;
}

std::string AudioEndpointGroupId(IMMDevice* device) {
  if (!device) {
    return {};
  }
  IPropertyStore* properties = nullptr;
  PROPVARIANT container_id;
  PropVariantInit(&container_id);
  std::string group_id;
  if (SUCCEEDED(device->OpenPropertyStore(STGM_READ, &properties)) &&
      SUCCEEDED(
          properties->GetValue(PKEY_Device_ContainerId, &container_id)) &&
      container_id.vt == VT_CLSID && container_id.puuid) {
    wchar_t value[64] = {};
    if (StringFromGUID2(*container_id.puuid, value,
                        static_cast<int>(sizeof(value) / sizeof(value[0]))) >
        0) {
      group_id = WideToUtf8(value);
    }
  }
  PropVariantClear(&container_id);
  SafeRelease(properties);
  return group_id;
}

bool AppendWindowsAudioEndpoints(EncodableList& sources,
                                 std::set<std::string>& seen,
                                 EDataFlow flow,
                                 const std::string& kind,
                                 const std::string& fallback) {
  ScopedComInitialization com;
  if (!com.ok()) {
    return false;
  }

  IMMDeviceEnumerator* enumerator = nullptr;
  IMMDeviceCollection* collection = nullptr;
  if (FAILED(CoCreateInstance(__uuidof(MMDeviceEnumerator), nullptr,
                              CLSCTX_ALL, IID_PPV_ARGS(&enumerator))) ||
      FAILED(enumerator->EnumAudioEndpoints(flow, DEVICE_STATE_ACTIVE,
                                            &collection))) {
    SafeRelease(collection);
    SafeRelease(enumerator);
    return false;
  }

  UINT count = 0;
  if (FAILED(collection->GetCount(&count))) {
    SafeRelease(collection);
    SafeRelease(enumerator);
    return false;
  }
  for (UINT i = 0; i < count; ++i) {
    IMMDevice* device = nullptr;
    if (FAILED(collection->Item(i, &device))) {
      continue;
    }
    const std::string device_id = AudioEndpointId(device);
    AppendAudioDevice(
        sources, seen, kind,
        AudioEndpointFriendlyName(device, fallback + " " + device_id),
        device_id, AudioEndpointGroupId(device));
    SafeRelease(device);
  }

  SafeRelease(collection);
  SafeRelease(enumerator);
  return true;
}
#endif

}  // namespace

namespace flutter_webrtc_plugin {

struct FlutterMediaStream::DeviceChangeState {
  explicit DeviceChangeState(EventChannelProxy* channel)
      : event_channel(channel) {}

  std::mutex mutex;
  EventChannelProxy* event_channel;
  bool active = true;
};

#if defined(_WIN32)
struct FlutterMediaStream::SourceEnumerationState {
  explicit SourceEnumerationState(TaskRunner* runner) : task_runner(runner) {}

  struct CachedVideoDevice {
    std::string name;
    std::string id;
    int index = 0;
  };

  std::mutex mutex;
  TaskRunner* task_runner;
  bool active = true;
  bool enumerating = false;
  uint64_t next_operation_id = 1;
  std::vector<std::shared_ptr<MethodResultProxy>> pending_results;
  std::map<uint64_t, std::shared_ptr<MethodResultProxy>> operation_results;
  std::vector<CachedVideoDevice> video_devices;
};

namespace {

const char* WorkerFailureCode(TimedSerialWorker::FailureReason reason) {
  switch (reason) {
    case TimedSerialWorker::FailureReason::kTimedOut:
      return "NativeDeviceOperationTimeout";
    case TimedSerialWorker::FailureReason::kStopped:
      return "NativeDeviceOperationStopped";
    case TimedSerialWorker::FailureReason::kUnavailable:
      return "NativeDeviceOperationUnavailable";
    case TimedSerialWorker::FailureReason::kFailed:
      return "NativeDeviceOperationFailed";
  }
  return "NativeDeviceOperationFailed";
}

const char* WorkerFailureMessage(TimedSerialWorker::FailureReason reason) {
  switch (reason) {
    case TimedSerialWorker::FailureReason::kTimedOut:
      return "The native media-device operation timed out.";
    case TimedSerialWorker::FailureReason::kStopped:
      return "The native media-device operation was stopped.";
    case TimedSerialWorker::FailureReason::kUnavailable:
      return "The native media-device worker is unavailable.";
    case TimedSerialWorker::FailureReason::kFailed:
      return "The native media-device operation failed.";
  }
  return "The native media-device operation failed.";
}

template <typename State>
void PostWindowsPlatformTask(
    const std::shared_ptr<State>& state,
    std::function<void()> task) {
  TaskRunner* runner = nullptr;
  {
    std::lock_guard<std::mutex> lock(state->mutex);
    if (!state->active || !state->task_runner) {
      return;
    }
    runner = state->task_runner;
    runner->EnqueueTask([state, task = std::move(task)]() mutable {
      {
        std::lock_guard<std::mutex> state_lock(state->mutex);
        if (!state->active) {
          return;
        }
      }
      task();
    });
  }
}

bool TrySelectAudioDevice(const scoped_refptr<RTCAudioDevice>& audio_device,
                          const std::string& device_id,
                          bool input) {
  if (!audio_device || device_id.empty()) {
    return false;
  }
  char device_name[RTCAudioDevice::kAdmMaxDeviceNameSize + 1] = {0};
  char device_guid[RTCAudioDevice::kAdmMaxGuidSize + 1] = {0};
  const int device_count =
      input ? audio_device->RecordingDevices()
            : audio_device->PlayoutDevices();
  for (int i = 0; i < device_count; ++i) {
    const int result =
        input ? audio_device->RecordingDeviceName(
                    static_cast<uint16_t>(i), device_name, device_guid)
              : audio_device->PlayoutDeviceName(
                    static_cast<uint16_t>(i), device_name, device_guid);
    if (result != 0) {
      continue;
    }
    if (device_id != AudioDeviceIdFromNameAndGuid(device_name, device_guid)) {
      continue;
    }
    return (input ? audio_device->SetRecordingDevice(
                        static_cast<uint16_t>(i))
                  : audio_device->SetPlayoutDevice(
                        static_cast<uint16_t>(i))) == 0;
  }
  return false;
}

}  // namespace
#endif

FlutterMediaStream::FlutterMediaStream(FlutterWebRTCBase* base) : base_(base) {
  device_change_state_ =
      std::make_shared<DeviceChangeState>(base->event_channel());
#if defined(_WIN32)
  source_enumeration_state_ =
      std::make_shared<SourceEnumerationState>(base->task_runner_);
  windows_device_worker_ = std::make_unique<TimedSerialWorker>();
  const auto change_state = device_change_state_;
  base_->audio_device_->OnDeviceChange([change_state] {
    std::lock_guard<std::mutex> lock(change_state->mutex);
    if (!change_state->active || !change_state->event_channel) {
      return;
    }
    EncodableMap info;
    info[EncodableValue("event")] = "onDeviceChange";
    change_state->event_channel->Success(EncodableValue(info), false);
  });
#else
  const auto change_state = device_change_state_;
  base_->audio_device_->OnDeviceChange([this, change_state] {
    std::lock_guard<std::mutex> lock(change_state->mutex);
    if (!change_state->active || !change_state->event_channel) {
      return;
    }
    ApplyDesiredAudioDevices();
    EncodableMap info;
    info[EncodableValue("event")] = "onDeviceChange";
    change_state->event_channel->Success(EncodableValue(info), false);
  });
#endif
}

FlutterMediaStream::~FlutterMediaStream() {
  base_->audio_device_->OnDeviceChange([] {});
  const auto change_state = device_change_state_;
  if (change_state) {
    std::lock_guard<std::mutex> lock(change_state->mutex);
    change_state->active = false;
    change_state->event_channel = nullptr;
  }
#if defined(_WIN32)
  const auto state = source_enumeration_state_;
  if (state) {
    std::lock_guard<std::mutex> lock(state->mutex);
    state->active = false;
    state->task_runner = nullptr;
    state->pending_results.clear();
    state->operation_results.clear();
    state->video_devices.clear();
  }
  if (windows_device_worker_ && !windows_device_worker_->Stop()) {
    base_->PreserveRuntimeForAbandonedNativeWork();
  }
  windows_device_worker_.reset();
#endif
}

void FlutterMediaStream::GetUserMedia(
    const EncodableMap& constraints,
    std::unique_ptr<MethodResultProxy> result) {
  std::string uuid = base_->GenerateUUID();
  scoped_refptr<RTCMediaStream> stream =
      base_->factory_->CreateStream(uuid.c_str());

  EncodableMap params;
  params[EncodableValue("streamId")] = EncodableValue(uuid);

  auto it = constraints.find(EncodableValue("audio"));
  if (it != constraints.end()) {
    EncodableValue audio = it->second;
    if (TypeIs<bool>(audio)) {
      if (true == GetValue<bool>(audio)) {
        GetUserAudio(constraints, stream, params);
      }
    } else if (TypeIs<EncodableMap>(audio)) {
      GetUserAudio(constraints, stream, params);
    } else {
      params[EncodableValue("audioTracks")] = EncodableValue(EncodableList());
    }
  } else {
    params[EncodableValue("audioTracks")] = EncodableValue(EncodableList());
  }

  it = constraints.find(EncodableValue("video"));
  params[EncodableValue("videoTracks")] = EncodableValue(EncodableList());
  if (it != constraints.end()) {
    EncodableValue video = it->second;
    if (TypeIs<bool>(video)) {
      if (true == GetValue<bool>(video)) {
        GetUserVideo(constraints, stream, params);
      }
    } else if (TypeIs<EncodableMap>(video)) {
      GetUserVideo(constraints, stream, params);
    }
  }

  base_->local_streams_[uuid] = stream;
  result->Success(EncodableValue(params));
}

void addDefaultAudioConstraints(
    scoped_refptr<RTCMediaConstraints> audioConstraints) {
  audioConstraints->AddOptionalConstraint("googNoiseSuppression", "true");
  audioConstraints->AddOptionalConstraint("googEchoCancellation", "true");
  audioConstraints->AddOptionalConstraint("echoCancellation", "true");
  audioConstraints->AddOptionalConstraint("googEchoCancellation2", "true");
  audioConstraints->AddOptionalConstraint("googDAEchoCancellation", "true");
}

std::string getSourceIdConstraint(const EncodableMap& mediaConstraints) {
  auto it = mediaConstraints.find(EncodableValue("optional"));
  if (it != mediaConstraints.end() && TypeIs<EncodableList>(it->second)) {
    EncodableList optional = GetValue<EncodableList>(it->second);
    for (size_t i = 0, size = optional.size(); i < size; i++) {
      if (TypeIs<EncodableMap>(optional[i])) {
        EncodableMap option = GetValue<EncodableMap>(optional[i]);
        auto it2 = option.find(EncodableValue("sourceId"));
        if (it2 != option.end() && TypeIs<std::string>(it2->second)) {
          return GetValue<std::string>(it2->second);
        }
      }
    }
  }
  return "";
}

std::string getDeviceIdConstraint(const EncodableMap& mediaConstraints) {
  auto it = mediaConstraints.find(EncodableValue("deviceId"));
  if (it != mediaConstraints.end() && TypeIs<std::string>(it->second)) {
    return GetValue<std::string>(it->second);
  }
  return "";
}

void FlutterMediaStream::GetUserAudio(const EncodableMap& constraints,
                                      scoped_refptr<RTCMediaStream> stream,
                                      EncodableMap& params) {
  bool enable_audio = false;
  scoped_refptr<RTCMediaConstraints> audioConstraints;
  std::string sourceId;
  std::string deviceId;
  auto it = constraints.find(EncodableValue("audio"));
  if (it != constraints.end()) {
    EncodableValue audio = it->second;
    if (TypeIs<bool>(audio)) {
      audioConstraints = RTCMediaConstraints::Create();
      addDefaultAudioConstraints(audioConstraints);
      enable_audio = GetValue<bool>(audio);
      sourceId = "";
      deviceId = "";
    }
    if (TypeIs<EncodableMap>(audio)) {
      EncodableMap localMap = GetValue<EncodableMap>(audio);
      sourceId = getSourceIdConstraint(localMap);
      deviceId = getDeviceIdConstraint(localMap);
      if (sourceId.empty()) {
        sourceId = deviceId;
      }
      audioConstraints = base_->ParseMediaConstraints(localMap);
      enable_audio = true;
    }
  }

  // Selecting audio input device by sourceId and audio output device by
  // deviceId

  if (enable_audio) {
#if defined(_WIN32)
    // MediaDeviceNative performs the requested Windows input selection through
    // the timed serial worker before invoking getUserMedia. Do not enumerate
    // ADM devices again on Flutter's platform thread here.
    if (sourceId.empty() && !desired_audio_input_device_id_.empty()) {
      sourceId = desired_audio_input_device_id_;
    }
#else
    char strRecordingName[256];
    char strRecordingGuid[256];
    int playout_devices = base_->audio_device_->PlayoutDevices();
    int recording_devices = base_->audio_device_->RecordingDevices();

    if (sourceId.empty() && !desired_audio_input_device_id_.empty()) {
      sourceId = desired_audio_input_device_id_;
    }
    if (deviceId.empty() && !desired_audio_output_device_id_.empty()) {
      deviceId = desired_audio_output_device_id_;
    }

    for (uint16_t i = 0; i < recording_devices; i++) {
      base_->audio_device_->RecordingDeviceName(i, strRecordingName,
                                                strRecordingGuid);
      const std::string recording_device_id =
          AudioDeviceIdFromNameAndGuid(strRecordingName, strRecordingGuid);
      if (!sourceId.empty() && sourceId == recording_device_id) {
        base_->audio_device_->SetRecordingDevice(i);
      }
    }

    if (sourceId == "" && recording_devices > 0) {
      base_->audio_device_->RecordingDeviceName(0, strRecordingName,
                                                strRecordingGuid);
      sourceId = AudioDeviceIdFromNameAndGuid(strRecordingName, strRecordingGuid);
    }

    char strPlayoutName[256];
    char strPlayoutGuid[256];
    for (uint16_t i = 0; i < playout_devices; i++) {
      base_->audio_device_->PlayoutDeviceName(i, strPlayoutName,
                                              strPlayoutGuid);
      const std::string playout_device_id =
          AudioDeviceIdFromNameAndGuid(strPlayoutName, strPlayoutGuid);
      if (!deviceId.empty() && deviceId == playout_device_id) {
        base_->audio_device_->SetPlayoutDevice(i);
      }
    }
#endif

    scoped_refptr<RTCAudioSource> source =
        base_->factory_->CreateAudioSource("audio_input");
    std::string uuid = base_->GenerateUUID();
    scoped_refptr<RTCAudioTrack> track =
        base_->factory_->CreateAudioTrack(source, uuid.c_str());

    std::string track_id = track->id().std_string();

    EncodableMap track_info;
    track_info[EncodableValue("id")] = EncodableValue(track->id().std_string());
    track_info[EncodableValue("label")] =
        EncodableValue(track->id().std_string());
    track_info[EncodableValue("kind")] =
        EncodableValue(track->kind().std_string());
    track_info[EncodableValue("enabled")] = EncodableValue(track->enabled());

    EncodableMap settings;
    settings[EncodableValue("deviceId")] = EncodableValue(sourceId);
    settings[EncodableValue("kind")] = EncodableValue("audioinput");
    settings[EncodableValue("autoGainControl")] = EncodableValue(true);
    settings[EncodableValue("echoCancellation")] = EncodableValue(true);
    settings[EncodableValue("noiseSuppression")] = EncodableValue(true);
    settings[EncodableValue("channelCount")] = EncodableValue(1);
    settings[EncodableValue("latency")] = EncodableValue(0);
    track_info[EncodableValue("settings")] = EncodableValue(settings);

    EncodableList audioTracks;
    audioTracks.push_back(EncodableValue(track_info));
    params[EncodableValue("audioTracks")] = EncodableValue(audioTracks);
    stream->AddTrack(track);

    base_->local_tracks_[track->id().std_string()] = track;
  }
}

std::string getFacingMode(const EncodableMap& mediaConstraints) {
  return mediaConstraints.find(EncodableValue("facingMode")) !=
                 mediaConstraints.end()
             ? GetValue<std::string>(
                   mediaConstraints.find(EncodableValue("facingMode"))->second)
             : "";
}

EncodableValue getConstrainInt(const EncodableMap& constraints,
                               const std::string& key) {
  EncodableValue value;
  auto it = constraints.find(EncodableValue(key));
  if (it != constraints.end()) {
    if (TypeIs<int>(it->second)) {
      return it->second;
    }

    if (TypeIs<EncodableMap>(it->second)) {
      EncodableMap innerMap = GetValue<EncodableMap>(it->second);
      auto it2 = innerMap.find(EncodableValue("ideal"));
      if (it2 != innerMap.end() && TypeIs<int>(it2->second)) {
        return it2->second;
      }
    }
  }

  return EncodableValue();
}

void FlutterMediaStream::GetUserVideo(const EncodableMap& constraints,
                                      scoped_refptr<RTCMediaStream> stream,
                                      EncodableMap& params) {
  EncodableMap video_constraints;
  EncodableMap video_mandatory;
  auto it = constraints.find(EncodableValue("video"));
  if (it != constraints.end() && TypeIs<EncodableMap>(it->second)) {
    video_constraints = GetValue<EncodableMap>(it->second);
    if (video_constraints.find(EncodableValue("mandatory")) !=
        video_constraints.end()) {
      video_mandatory = GetValue<EncodableMap>(
          video_constraints.find(EncodableValue("mandatory"))->second);
    }
  }

  std::string facing_mode = getFacingMode(video_constraints);
  // bool isFacing = facing_mode == "" || facing_mode != "environment";
  std::string sourceId = getSourceIdConstraint(video_constraints);

  EncodableValue widthValue = getConstrainInt(video_constraints, "width");

  if (widthValue == EncodableValue())
    widthValue = findEncodableValue(video_mandatory, "minWidth");

  if (widthValue == EncodableValue())
    widthValue = findEncodableValue(video_mandatory, "width");

  EncodableValue heightValue = getConstrainInt(video_constraints, "height");

  if (heightValue == EncodableValue())
    heightValue = findEncodableValue(video_mandatory, "minHeight");

  if (heightValue == EncodableValue())
    heightValue = findEncodableValue(video_mandatory, "height");

  EncodableValue fpsValue = getConstrainInt(video_constraints, "frameRate");

  if (fpsValue == EncodableValue())
    fpsValue = findEncodableValue(video_mandatory, "minFrameRate");

  if (fpsValue == EncodableValue())
    fpsValue = findEncodableValue(video_mandatory, "frameRate");

  scoped_refptr<RTCVideoCapturer> video_capturer;
#if defined(_WIN32)
  std::vector<SourceEnumerationState::CachedVideoDevice> cached_devices;
  {
    std::lock_guard<std::mutex> lock(source_enumeration_state_->mutex);
    cached_devices = source_enumeration_state_->video_devices;
  }
  const int nb_video_devices = static_cast<int>(cached_devices.size());
#else
  char strNameUTF8[256];
  char strGuidUTF8[256];
  int nb_video_devices = base_->video_device_->NumberOfDevices();
#endif

  int32_t width = toInt(widthValue, DEFAULT_WIDTH);
  int32_t height = toInt(heightValue, DEFAULT_HEIGHT);
  int32_t fps = toInt(fpsValue, DEFAULT_FPS);

#if defined(_WIN32)
  for (const auto& device : cached_devices) {
    if (!sourceId.empty() && sourceId == device.id) {
      video_capturer = base_->video_device_->Create(
          device.name.c_str(), device.index, width, height, fps);
      break;
    }
  }
#else
  for (int i = 0; i < nb_video_devices; i++) {
    base_->video_device_->GetDeviceName(i, strNameUTF8, 256, strGuidUTF8, 256);
    if (sourceId != "" && sourceId == strGuidUTF8) {
      video_capturer =
          base_->video_device_->Create(strNameUTF8, i, width, height, fps);
      break;
    }
  }
#endif

  if (nb_video_devices == 0)
    return;

  if (!video_capturer.get()) {
#if defined(_WIN32)
    const auto& device = cached_devices.front();
    sourceId = device.id;
    video_capturer = base_->video_device_->Create(
        device.name.c_str(), device.index, width, height, fps);
#else
    base_->video_device_->GetDeviceName(0, strNameUTF8, 128, strGuidUTF8, 128);
    sourceId = strGuidUTF8;
    video_capturer =
        base_->video_device_->Create(strNameUTF8, 0, width, height, fps);
#endif
  }

  if (!video_capturer.get())
    return;

  video_capturer->StartCapture();

  const char* video_source_label = "video_input";
  scoped_refptr<RTCVideoSource> source = base_->factory_->CreateVideoSource(
      video_capturer, video_source_label,
      base_->ParseMediaConstraints(video_constraints));

  std::string uuid = base_->GenerateUUID();
  scoped_refptr<RTCVideoTrack> track =
      base_->factory_->CreateVideoTrack(source, uuid.c_str());

  EncodableList videoTracks;
  EncodableMap info;
  info[EncodableValue("id")] = EncodableValue(track->id().std_string());
  info[EncodableValue("label")] = EncodableValue(track->id().std_string());
  info[EncodableValue("kind")] = EncodableValue(track->kind().std_string());
  info[EncodableValue("enabled")] = EncodableValue(track->enabled());

  EncodableMap settings;
  settings[EncodableValue("deviceId")] = EncodableValue(sourceId);
  settings[EncodableValue("kind")] = EncodableValue("videoinput");
  settings[EncodableValue("width")] = EncodableValue(width);
  settings[EncodableValue("height")] = EncodableValue(height);
  settings[EncodableValue("frameRate")] = EncodableValue(fps);
  info[EncodableValue("settings")] = EncodableValue(settings);

  videoTracks.push_back(EncodableValue(info));
  params[EncodableValue("videoTracks")] = EncodableValue(videoTracks);

  stream->AddTrack(track);

  base_->local_tracks_[track->id().std_string()] = track;
  base_->video_capturers_[track->id().std_string()] = video_capturer;
}

void FlutterMediaStream::GetSources(std::unique_ptr<MethodResultProxy> result) {
#if defined(_WIN32)
  const auto state = source_enumeration_state_;
  const auto video_device = base_->video_device_;
  const auto shared_result =
      std::shared_ptr<MethodResultProxy>(std::move(result));
  bool reject_request = false;
  {
    std::lock_guard<std::mutex> lock(state->mutex);
    if (!state->active) {
      return;
    }
    if (state->pending_results.size() >= 32) {
      reject_request = true;
    } else {
      state->pending_results.push_back(shared_result);
      if (state->enumerating) {
        return;
      }
      state->enumerating = true;
    }
  }
  if (reject_request) {
    shared_result->Error(
        "NativeDeviceOperationUnavailable",
        "Too many media-device enumeration requests are pending.");
    return;
  }

  windows_device_worker_->Submit(
      [state, video_device]() -> TimedSerialWorker::Completion {
        EncodableList sources;
        std::set<std::string> seen_audio_devices;
        if (!AppendWindowsAudioEndpoints(sources, seen_audio_devices, eCapture,
                                         "audioinput", "Microphone") ||
            !AppendWindowsAudioEndpoints(sources, seen_audio_devices, eRender,
                                         "audiooutput", "Speaker")) {
          throw std::runtime_error(
              "Windows audio endpoint enumeration failed.");
        }

        std::vector<SourceEnumerationState::CachedVideoDevice> video_cache;
        char name[RTCAudioDevice::kAdmMaxDeviceNameSize + 1] = {0};
        char guid[RTCAudioDevice::kAdmMaxGuidSize + 1] = {0};
        const int video_devices = video_device->NumberOfDevices();
        for (int i = 0; i < video_devices; i++) {
          if (video_device->GetDeviceName(i, name, 128, guid, 128) != 0) {
            continue;
          }
          SourceEnumerationState::CachedVideoDevice cached;
          cached.name = name;
          cached.id = guid;
          cached.index = i;
          video_cache.push_back(cached);

          EncodableMap video;
          video[EncodableValue("label")] =
              EncodableValue(std::string(name));
          video[EncodableValue("deviceId")] =
              EncodableValue(std::string(guid));
          video[EncodableValue("facing")] = i == 1 ? "front" : "back";
          video[EncodableValue("kind")] = "videoinput";
          sources.push_back(EncodableValue(video));
        }

        EncodableMap params;
        params[EncodableValue("sources")] = EncodableValue(sources);
        const EncodableValue response(params);
        return [state, response, video_cache = std::move(video_cache)]() {
          PostWindowsPlatformTask(
              state, [state, response, video_cache]() {
                std::vector<std::shared_ptr<MethodResultProxy>> results;
                {
                  std::lock_guard<std::mutex> result_lock(state->mutex);
                  if (!state->active) {
                    state->pending_results.clear();
                    state->enumerating = false;
                    return;
                  }
                  state->video_devices = video_cache;
                  state->enumerating = false;
                  results.swap(state->pending_results);
                }
                for (const auto& pending : results) {
                  pending->Success(response);
                }
              });
        };
      },
      [state](TimedSerialWorker::FailureReason reason) {
        PostWindowsPlatformTask(state, [state, reason]() {
          std::vector<std::shared_ptr<MethodResultProxy>> results;
          {
            std::lock_guard<std::mutex> result_lock(state->mutex);
            state->enumerating = false;
            results.swap(state->pending_results);
          }
          for (const auto& pending : results) {
            pending->Error(WorkerFailureCode(reason),
                           WorkerFailureMessage(reason));
          }
        });
      });
  return;
#else
  EncodableList sources;
  std::set<std::string> seen_audio_devices;

  int nb_audio_devices = base_->audio_device_->RecordingDevices();
  char strNameUTF8[RTCAudioDevice::kAdmMaxDeviceNameSize + 1] = {0};
  char strGuidUTF8[RTCAudioDevice::kAdmMaxGuidSize + 1] = {0};

  for (uint16_t i = 0; i < nb_audio_devices; i++) {
    base_->audio_device_->RecordingDeviceName(i, strNameUTF8, strGuidUTF8);
    AppendAudioDevice(
        sources, seen_audio_devices, "audioinput", std::string(strNameUTF8),
        AudioDeviceIdFromNameAndGuid(strNameUTF8, strGuidUTF8));
  }

  nb_audio_devices = base_->audio_device_->PlayoutDevices();
  for (uint16_t i = 0; i < nb_audio_devices; i++) {
    base_->audio_device_->PlayoutDeviceName(i, strNameUTF8, strGuidUTF8);
    AppendAudioDevice(
        sources, seen_audio_devices, "audiooutput", std::string(strNameUTF8),
        AudioDeviceIdFromNameAndGuid(strNameUTF8, strGuidUTF8));
  }

  int nb_video_devices = base_->video_device_->NumberOfDevices();
  for (int i = 0; i < nb_video_devices; i++) {
    base_->video_device_->GetDeviceName(i, strNameUTF8, 128, strGuidUTF8, 128);
    EncodableMap video;
    video[EncodableValue("label")] = EncodableValue(std::string(strNameUTF8));
    video[EncodableValue("deviceId")] =
        EncodableValue(std::string(strGuidUTF8));
    video[EncodableValue("facing")] = i == 1 ? "front" : "back";
    video[EncodableValue("kind")] = "videoinput";
    sources.push_back(EncodableValue(video));
  }
  EncodableMap params;
  params[EncodableValue("sources")] = EncodableValue(sources);
  result->Success(EncodableValue(params));
#endif
}

bool FlutterMediaStream::TrySelectAudioOutputDevice(
    const std::string& device_id) {
#if defined(_WIN32)
  // Windows selection is always dispatched through windows_device_worker_.
  return false;
#else
  if (device_id.empty()) {
    return false;
  }
  char deviceName[256];
  char deviceGuid[256];
  int playout_devices = base_->audio_device_->PlayoutDevices();
  for (uint16_t i = 0; i < playout_devices; i++) {
    base_->audio_device_->PlayoutDeviceName(i, deviceName, deviceGuid);
    const std::string cur_device_id =
        AudioDeviceIdFromNameAndGuid(deviceName, deviceGuid);
    if (device_id == cur_device_id) {
      base_->audio_device_->SetPlayoutDevice(i);
      return true;
    }
  }
  return false;
#endif
}

void FlutterMediaStream::SelectAudioOutput(
    const std::string& device_id,
    std::unique_ptr<MethodResultProxy> result) {
  desired_audio_output_device_id_ = device_id;
#if defined(_WIN32)
  const auto state = source_enumeration_state_;
  const auto audio_device = base_->audio_device_;
  const auto shared_result =
      std::shared_ptr<MethodResultProxy>(std::move(result));
  uint64_t operation_id = 0;
  bool reject_request = false;
  {
    std::lock_guard<std::mutex> lock(state->mutex);
    if (!state->active || state->operation_results.size() >= 32) {
      reject_request = true;
    } else {
      operation_id = state->next_operation_id++;
      state->operation_results[operation_id] = shared_result;
    }
  }
  if (reject_request) {
    shared_result->Error(
        "NativeDeviceOperationUnavailable",
        "The native media-device worker is unavailable.");
    return;
  }
  windows_device_worker_->Submit(
      [state, audio_device, device_id,
       operation_id]() -> TimedSerialWorker::Completion {
        const bool selected =
            TrySelectAudioDevice(audio_device, device_id, false);
        return [state, operation_id, selected, device_id]() {
          PostWindowsPlatformTask(
              state, [state, operation_id, selected, device_id]() {
                std::shared_ptr<MethodResultProxy> operation_result;
                {
                  std::lock_guard<std::mutex> lock(state->mutex);
                  const auto found =
                      state->operation_results.find(operation_id);
                  if (found == state->operation_results.end()) {
                    return;
                  }
                  operation_result = std::move(found->second);
                  state->operation_results.erase(found);
                }
                if (selected) {
                  operation_result->Success();
                } else if (!device_id.empty()) {
                  operation_result->Success(EncodableValue(EncodableMap{
                      {EncodableValue("deferred"), EncodableValue(true)}}));
                } else {
                  operation_result->Error(
                      "Bad Arguments",
                      "Not found device id: " + device_id);
                }
              });
        };
      },
      [state, operation_id](TimedSerialWorker::FailureReason reason) {
        PostWindowsPlatformTask(state, [state, operation_id, reason]() {
          std::shared_ptr<MethodResultProxy> operation_result;
          {
            std::lock_guard<std::mutex> lock(state->mutex);
            const auto found = state->operation_results.find(operation_id);
            if (found == state->operation_results.end()) {
              return;
            }
            operation_result = std::move(found->second);
            state->operation_results.erase(found);
          }
          operation_result->Error(WorkerFailureCode(reason),
                                  WorkerFailureMessage(reason));
        });
      });
  return;
#else
  if (TrySelectAudioOutputDevice(device_id)) {
    result->Success();
    return;
  }
  {
    result->Error("Bad Arguments", "Not found device id: " + device_id);
    return;
  }
#endif
}

bool FlutterMediaStream::TrySelectAudioInputDevice(
    const std::string& device_id) {
#if defined(_WIN32)
  // Windows selection is always dispatched through windows_device_worker_.
  return false;
#else
  if (device_id.empty()) {
    return false;
  }
  char deviceName[256];
  char deviceGuid[256];
  int recording_devices = base_->audio_device_->RecordingDevices();
  for (uint16_t i = 0; i < recording_devices; i++) {
    base_->audio_device_->RecordingDeviceName(i, deviceName, deviceGuid);
    const std::string cur_device_id =
        AudioDeviceIdFromNameAndGuid(deviceName, deviceGuid);
    if (device_id == cur_device_id) {
      base_->audio_device_->SetRecordingDevice(i);
      return true;
    }
  }
  return false;
#endif
}

void FlutterMediaStream::SelectAudioInput(
    const std::string& device_id,
    std::unique_ptr<MethodResultProxy> result) {
  desired_audio_input_device_id_ = device_id;
#if defined(_WIN32)
  const auto state = source_enumeration_state_;
  const auto audio_device = base_->audio_device_;
  const auto shared_result =
      std::shared_ptr<MethodResultProxy>(std::move(result));
  uint64_t operation_id = 0;
  bool reject_request = false;
  {
    std::lock_guard<std::mutex> lock(state->mutex);
    if (!state->active || state->operation_results.size() >= 32) {
      reject_request = true;
    } else {
      operation_id = state->next_operation_id++;
      state->operation_results[operation_id] = shared_result;
    }
  }
  if (reject_request) {
    shared_result->Error(
        "NativeDeviceOperationUnavailable",
        "The native media-device worker is unavailable.");
    return;
  }
  windows_device_worker_->Submit(
      [state, audio_device, device_id,
       operation_id]() -> TimedSerialWorker::Completion {
        const bool selected =
            TrySelectAudioDevice(audio_device, device_id, true);
        return [state, operation_id, selected, device_id]() {
          PostWindowsPlatformTask(
              state, [state, operation_id, selected, device_id]() {
                std::shared_ptr<MethodResultProxy> operation_result;
                {
                  std::lock_guard<std::mutex> lock(state->mutex);
                  const auto found =
                      state->operation_results.find(operation_id);
                  if (found == state->operation_results.end()) {
                    return;
                  }
                  operation_result = std::move(found->second);
                  state->operation_results.erase(found);
                }
                if (selected) {
                  operation_result->Success();
                } else if (!device_id.empty()) {
                  operation_result->Success(EncodableValue(EncodableMap{
                      {EncodableValue("deferred"), EncodableValue(true)}}));
                } else {
                  operation_result->Error(
                      "Bad Arguments",
                      "Not found device id: " + device_id);
                }
              });
        };
      },
      [state, operation_id](TimedSerialWorker::FailureReason reason) {
        PostWindowsPlatformTask(state, [state, operation_id, reason]() {
          std::shared_ptr<MethodResultProxy> operation_result;
          {
            std::lock_guard<std::mutex> lock(state->mutex);
            const auto found = state->operation_results.find(operation_id);
            if (found == state->operation_results.end()) {
              return;
            }
            operation_result = std::move(found->second);
            state->operation_results.erase(found);
          }
          operation_result->Error(WorkerFailureCode(reason),
                                  WorkerFailureMessage(reason));
        });
      });
  return;
#else
  if (TrySelectAudioInputDevice(device_id)) {
    result->Success();
    return;
  }
  {
    result->Error("Bad Arguments", "Not found device id: " + device_id);
    return;
  }
#endif
}

void FlutterMediaStream::ApplyDesiredAudioDevices() {
  if (!desired_audio_input_device_id_.empty()) {
    TrySelectAudioInputDevice(desired_audio_input_device_id_);
  }
  if (!desired_audio_output_device_id_.empty()) {
    TrySelectAudioOutputDevice(desired_audio_output_device_id_);
  }
}

void FlutterMediaStream::MediaStreamGetTracks(
    const std::string& stream_id,
    std::unique_ptr<MethodResultProxy> result) {
  scoped_refptr<RTCMediaStream> stream = base_->MediaStreamForId(stream_id);

  if (stream) {
    EncodableMap params;
    EncodableList audioTracks;

    auto audio_tracks = stream->audio_tracks();
    for (auto track : audio_tracks.std_vector()) {
      base_->local_tracks_[track->id().std_string()] = track;
      EncodableMap info;
      info[EncodableValue("id")] = EncodableValue(track->id().std_string());
      info[EncodableValue("label")] = EncodableValue(track->id().std_string());
      info[EncodableValue("kind")] = EncodableValue(track->kind().std_string());
      info[EncodableValue("enabled")] = EncodableValue(track->enabled());
      info[EncodableValue("remote")] = EncodableValue(true);
      info[EncodableValue("readyState")] = "live";
      audioTracks.push_back(EncodableValue(info));
    }
    params[EncodableValue("audioTracks")] = EncodableValue(audioTracks);

    EncodableList videoTracks;
    auto video_tracks = stream->video_tracks();
    for (auto track : video_tracks.std_vector()) {
      base_->local_tracks_[track->id().std_string()] = track;
      EncodableMap info;
      info[EncodableValue("id")] = EncodableValue(track->id().std_string());
      info[EncodableValue("label")] = EncodableValue(track->id().std_string());
      info[EncodableValue("kind")] = EncodableValue(track->kind().std_string());
      info[EncodableValue("enabled")] = EncodableValue(track->enabled());
      info[EncodableValue("remote")] = EncodableValue(true);
      info[EncodableValue("readyState")] = "live";
      videoTracks.push_back(EncodableValue(info));
    }

    params[EncodableValue("videoTracks")] = EncodableValue(videoTracks);

    result->Success(EncodableValue(params));
  } else {
    result->Error("MediaStreamGetTracksFailed",
                  "MediaStreamGetTracks() media stream is null !");
  }
}

void FlutterMediaStream::MediaStreamDispose(
    const std::string& stream_id,
    std::unique_ptr<MethodResultProxy> result) {
  scoped_refptr<RTCMediaStream> stream = base_->MediaStreamForId(stream_id);

  if (!stream) {
    result->Error("MediaStreamDisposeFailed",
                  "stream [" + stream_id + "] not found!");
    return;
  }

  vector<scoped_refptr<RTCAudioTrack>> audio_tracks = stream->audio_tracks();

  for (auto track : audio_tracks.std_vector()) {
    stream->RemoveTrack(track);
    base_->RemoveMediaTrackForId(track->id().std_string());
  }

  vector<scoped_refptr<RTCVideoTrack>> video_tracks = stream->video_tracks();
  for (auto track : video_tracks.std_vector()) {
    stream->RemoveTrack(track);
    if (base_->video_capturers_.find(track->id().std_string()) !=
        base_->video_capturers_.end()) {
      auto video_capture = base_->video_capturers_[track->id().std_string()];
      if (video_capture->CaptureStarted()) {
        video_capture->StopCapture();
      }
      base_->video_capturers_.erase(track->id().std_string());
    }
    base_->RemoveMediaTrackForId(track->id().std_string());
  }

  base_->RemoveStreamForId(stream_id);
  result->Success();
}

void FlutterMediaStream::CreateLocalMediaStream(
    std::unique_ptr<MethodResultProxy> result) {
  std::string uuid = base_->GenerateUUID();
  scoped_refptr<RTCMediaStream> stream =
      base_->factory_->CreateStream(uuid.c_str());

  EncodableMap params;
  params[EncodableValue("streamId")] = EncodableValue(uuid);

  base_->local_streams_[uuid] = stream;
  result->Success(EncodableValue(params));
}

void FlutterMediaStream::MediaStreamTrackSetEnable(
    const std::string& track_id,
    std::unique_ptr<MethodResultProxy> result) {
  result->NotImplemented();
}

void FlutterMediaStream::MediaStreamTrackSwitchCamera(
    const std::string& track_id,
    std::unique_ptr<MethodResultProxy> result) {
  result->NotImplemented();
}

void FlutterMediaStream::MediaStreamTrackDispose(
    const std::string& track_id,
    std::unique_ptr<MethodResultProxy> result) {
  for (auto it : base_->local_streams_) {
    auto stream = it.second;
    auto audio_tracks = stream->audio_tracks();
    for (auto track : audio_tracks.std_vector()) {
      if (track->id().std_string() == track_id) {
        stream->RemoveTrack(track);
      }
    }
    auto video_tracks = stream->video_tracks();
    for (auto track : video_tracks.std_vector()) {
      if (track->id().std_string() == track_id) {
        stream->RemoveTrack(track);

        if (base_->video_capturers_.find(track_id) !=
            base_->video_capturers_.end()) {
          auto video_capture = base_->video_capturers_[track_id];
          if (video_capture->CaptureStarted()) {
            video_capture->StopCapture();
          }
          base_->video_capturers_.erase(track_id);
        }
      }
    }
  }
  base_->RemoveMediaTrackForId(track_id);
  result->Success();
}
}  // namespace flutter_webrtc_plugin
