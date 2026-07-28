#ifndef RUNNER_FLUTTER_WINDOW_H_
#define RUNNER_FLUTTER_WINDOW_H_

#include <flutter/dart_project.h>
#include <flutter/encodable_value.h>
#include <flutter/flutter_view_controller.h>
#include <flutter/method_channel.h>
#include <shellapi.h>

#include <functional>
#include <memory>

#include "win32_window.h"

struct IMMDeviceEnumerator;
struct IMMNotificationClient;
struct AudioEndpointQueryState;

namespace flutter_webrtc_plugin {
class TimedSerialWorker;
}

// A window that does nothing but host a Flutter view.
class FlutterWindow : public Win32Window {
 public:
  // Creates a new FlutterWindow hosting a Flutter view running |project|.
  explicit FlutterWindow(const flutter::DartProject& project);
  virtual ~FlutterWindow();

  void HandleNativeFileDrop(HWND window, HDROP drop);

 protected:
  // Win32Window:
  bool OnCreate() override;
  void OnDestroy() override;
  LRESULT MessageHandler(HWND window, UINT const message, WPARAM const wparam,
                         LPARAM const lparam) noexcept override;

 private:
  // The project to run.
  flutter::DartProject project_;

  // The Flutter instance hosted by this window.
  std::unique_ptr<flutter::FlutterViewController> flutter_controller_;

  // Native clipboard bridge used by the message input to paste Windows files.
  std::unique_ptr<flutter::MethodChannel<flutter::EncodableValue>>
      clipboard_channel_;

  // Native file-drop bridge used to upload files dropped onto the composer.
  std::unique_ptr<flutter::MethodChannel<flutter::EncodableValue>>
      file_drop_channel_;

  // Native desktop audio bridge used by Settings to follow system defaults.
  std::unique_ptr<flutter::MethodChannel<flutter::EncodableValue>>
      audio_devices_channel_;

  // Native Windows notification-area bridge used for minimize-to-tray.
  std::unique_ptr<flutter::MethodChannel<flutter::EncodableValue>>
      tray_channel_;
  bool tray_icon_added_ = false;
  bool attention_timer_active_ = false;
  bool attention_stop_posted_ = false;
  bool taskbar_attention_active_ = false;
  bool tray_attention_icon_visible_ = true;
  bool media_playback_active_ = false;
  HICON tray_attention_blank_icon_ = nullptr;

  IMMDeviceEnumerator* audio_device_enumerator_ = nullptr;
  IMMNotificationClient* audio_device_notification_client_ = nullptr;
  bool audio_com_initialized_here_ = false;
  std::shared_ptr<AudioEndpointQueryState> audio_endpoint_query_state_;
  std::unique_ptr<flutter_webrtc_plugin::TimedSerialWorker>
      audio_endpoint_worker_;

  HWND file_drop_child_window_ = nullptr;
  WNDPROC original_child_proc_ = nullptr;

  void RegisterAudioDevicesChannel();
  void SubmitAudioEndpointQuery(
      std::function<flutter::EncodableValue()> query,
      std::unique_ptr<flutter::MethodResult<flutter::EncodableValue>> result);
  void StopAudioEndpointQueries();
  void RegisterTrayChannel();
  bool ShowTrayIcon();
  void RemoveTrayIcon();
  void RequestMessageAttention();
  bool SetMediaPlaybackActive(bool active);
  void StartMessageAttentionTimer();
  void ScheduleMessageAttentionStop();
  void StopMessageAttention();
  void TickMessageAttention();
  bool SetTrayIconImage(HICON icon);
  void ShowTrayMenu();
  void InvokeTrayMethod(const char* method);
  bool EnsureAudioDeviceNotifications();
  void DetachAudioDeviceNotifications();
  void AttachFileDropTarget(HWND child_window);
  void DetachFileDropTarget();
};

#endif  // RUNNER_FLUTTER_WINDOW_H_
