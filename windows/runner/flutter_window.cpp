#include "flutter_window.h"

#include <propkeydef.h>
#include <functiondiscoverykeys_devpkey.h>
#include <flutter/standard_method_codec.h>
#include <mmdeviceapi.h>
#include <propsys.h>
#include <shellapi.h>
#include <shlobj_core.h>
#include <wincodec.h>
#include <windowsx.h>

#include <cstdint>
#include <cwchar>
#include <cstring>
#include <functional>
#include <memory>
#include <optional>
#include <string>
#include <utility>
#include <variant>
#include <vector>

#include "flutter/generated_plugin_registrant.h"
#include "resource.h"

namespace {

constexpr char kClipboardChannelName[] = "gang_chat/clipboard";
constexpr char kFileDropChannelName[] = "gang_chat/file_drop";
constexpr char kAudioDevicesChannelName[] = "gang_chat/audio_devices";
constexpr char kTrayChannelName[] = "gang_chat/tray";
constexpr char kReadFilePathsMethod[] = "readFilePaths";
constexpr char kReadImageFileMethod[] = "readImageFile";
constexpr char kWriteFilePathsMethod[] = "writeFilePaths";
constexpr char kWriteImageFileMethod[] = "writeImageFile";
constexpr char kDropFilesMethod[] = "dropFiles";
constexpr char kEnumerateInputsMethod[] = "enumerateInputs";
constexpr char kEnumerateOutputsMethod[] = "enumerateOutputs";
constexpr char kDefaultInputDeviceIdMethod[] = "getDefaultInputDeviceId";
constexpr char kDefaultOutputDeviceIdMethod[] = "getDefaultOutputDeviceId";
constexpr char kStartListeningMethod[] = "startListening";
constexpr char kDefaultInputDeviceChangedMethod[] = "defaultInputDeviceChanged";
constexpr char kDefaultOutputDeviceChangedMethod[] =
    "defaultOutputDeviceChanged";
constexpr char kTrayInitializeMethod[] = "initialize";
constexpr char kTrayDisposeMethod[] = "dispose";
constexpr char kTrayRequestAttentionMethod[] = "requestAttention";
constexpr char kSetMediaPlaybackActiveMethod[] = "setMediaPlaybackActive";
constexpr char kTrayOpenMethod[] = "open";
constexpr char kTrayExitMethod[] = "exit";
constexpr wchar_t kFileDropWindowProp[] = L"GangChatFileDropWindow";
constexpr wchar_t kFileDropOriginalProcProp[] =
    L"GangChatFileDropOriginalProc";
constexpr DWORD kBiAlphaBitFields = 6;
constexpr UINT kAudioDefaultDeviceChangedMessage = WM_APP + 0x4A2;
constexpr UINT kTrayCallbackMessage = WM_APP + 0x4A3;
constexpr UINT kTrayMenuCommandMessage = WM_APP + 0x4A4;
constexpr UINT kStopMessageAttentionMessage = WM_APP + 0x4A5;
constexpr UINT kTrayIconId = 1;
constexpr UINT kTrayOpenCommand = 0x4A30;
constexpr UINT kTrayExitCommand = 0x4A31;
constexpr UINT kTraySeparatorCommand = 0x4A32;
constexpr UINT_PTR kMessageAttentionTimerId = 0x4A33;
constexpr UINT kMessageAttentionIntervalMs = 480;
constexpr wchar_t kTrayPopupMenuClassName[] = L"GangChatTrayPopupMenu";
constexpr int kTrayMenuWidth = 184;
constexpr int kTrayMenuItemHeight = 32;
constexpr int kTrayMenuSeparatorHeight = 10;
constexpr int kTrayMenuHorizontalPadding = 12;
constexpr int kTrayMenuVerticalPadding = 5;
constexpr int kTrayMenuDividerInset = 8;
constexpr int kTrayMenuDividerHeight = 2;
constexpr int kTrayMenuFontSize = 13;
constexpr int kTrayMenuCornerRadius = 8;

struct TrayMenuItem {
  UINT command;
  const wchar_t* label;
  bool separator;
};

const TrayMenuItem kTrayOpenMenuItem{kTrayOpenCommand,
                                     L"\u6253\u5f00 Gang Chat", false};
const TrayMenuItem kTraySeparatorMenuItem{kTraySeparatorCommand, L"", true};
const TrayMenuItem kTrayExitMenuItem{kTrayExitCommand, L"\u9000\u51fa",
                                     false};

HICON CreateTransparentTrayIcon() {
  const int width = GetSystemMetrics(SM_CXSMICON);
  const int height = GetSystemMetrics(SM_CYSMICON);
  if (width <= 0 || height <= 0) {
    return nullptr;
  }
  const int row_bytes = ((width + 15) / 16) * 2;
  std::vector<BYTE> and_mask(row_bytes * height, 0xFF);
  std::vector<BYTE> xor_mask(row_bytes * height, 0x00);
  return CreateIcon(GetModuleHandle(nullptr), width, height, 1, 1,
                    and_mask.data(), xor_mask.data());
}

struct TrayPopupMenuState {
  explicit TrayPopupMenuState(HWND owner_window) : owner(owner_window) {}

  HWND owner = nullptr;
  UINT hovered_command = 0;
};

template <typename T>
void SafeRelease(T*& value) {
  if (value) {
    value->Release();
    value = nullptr;
  }
}

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

std::wstring Utf8ToWide(const std::string& value) {
  if (value.empty()) {
    return {};
  }

  const int size = MultiByteToWideChar(CP_UTF8, 0, value.c_str(), -1, nullptr, 0);
  if (size <= 1) {
    return {};
  }

  std::wstring result(size, L'\0');
  MultiByteToWideChar(CP_UTF8, 0, value.c_str(), -1, result.data(), size);
  result.pop_back();
  return result;
}

bool EnsureComInitialized(bool* initialized_here) {
  *initialized_here = false;
  const HRESULT result = CoInitializeEx(nullptr, COINIT_APARTMENTTHREADED);
  if (SUCCEEDED(result)) {
    *initialized_here = true;
    return true;
  }
  return result == RPC_E_CHANGED_MODE;
}

int ScaleForWindowDpi(HWND window, int value) {
  const UINT dpi = window ? GetDpiForWindow(window) : USER_DEFAULT_SCREEN_DPI;
  return MulDiv(value, dpi, USER_DEFAULT_SCREEN_DPI);
}

int TrayMenuPopupWidth(HWND window) {
  return ScaleForWindowDpi(window, kTrayMenuWidth);
}

int TrayMenuPopupHeight(HWND window) {
  return ScaleForWindowDpi(window, kTrayMenuVerticalPadding * 2 +
                                       kTrayMenuItemHeight * 2 +
                                       kTrayMenuSeparatorHeight);
}

RECT TrayMenuItemRect(HWND window, const TrayMenuItem& item) {
  const int padding = ScaleForWindowDpi(window, kTrayMenuVerticalPadding);
  const int item_height = ScaleForWindowDpi(window, kTrayMenuItemHeight);
  const int separator_height =
      ScaleForWindowDpi(window, kTrayMenuSeparatorHeight);
  const int width = TrayMenuPopupWidth(window);

  if (item.command == kTrayOpenCommand) {
    return RECT{0, padding, width, padding + item_height};
  }
  if (item.command == kTraySeparatorCommand) {
    return RECT{0, padding + item_height, width,
                padding + item_height + separator_height};
  }
  return RECT{0, padding + item_height + separator_height, width,
              padding + item_height + separator_height + item_height};
}

const TrayMenuItem* TrayMenuItemAtPoint(HWND window, POINT point) {
  for (const TrayMenuItem* item :
       {&kTrayOpenMenuItem, &kTraySeparatorMenuItem, &kTrayExitMenuItem}) {
    if (item->separator) {
      continue;
    }
    RECT item_rect = TrayMenuItemRect(window, *item);
    if (PtInRect(&item_rect, point)) {
      return item;
    }
  }
  return nullptr;
}

void DrawTrayPopupMenu(HWND window, TrayPopupMenuState* state, HDC hdc) {
  RECT client{};
  GetClientRect(window, &client);
  const int width = client.right - client.left;
  const int height = client.bottom - client.top;
  const int radius = ScaleForWindowDpi(window, kTrayMenuCornerRadius);
  const int diameter = radius * 2;

  HDC memory_dc = CreateCompatibleDC(hdc);
  HBITMAP bitmap = CreateCompatibleBitmap(hdc, width, height);
  HBITMAP old_bitmap = static_cast<HBITMAP>(SelectObject(memory_dc, bitmap));
  const int saved = SaveDC(memory_dc);

  HBRUSH white = CreateSolidBrush(RGB(255, 255, 255));
  HPEN no_pen = static_cast<HPEN>(GetStockObject(NULL_PEN));
  HPEN old_pen = static_cast<HPEN>(SelectObject(memory_dc, no_pen));
  HBRUSH old_brush = static_cast<HBRUSH>(SelectObject(memory_dc, white));
  RoundRect(memory_dc, 0, 0, width, height, diameter, diameter);
  SelectObject(memory_dc, old_brush);
  SelectObject(memory_dc, old_pen);

  HRGN clip = CreateRoundRectRgn(0, 0, width + 1, height + 1, diameter,
                                 diameter);
  SelectClipRgn(memory_dc, clip);
  DeleteObject(clip);

  for (const TrayMenuItem* item :
       {&kTrayOpenMenuItem, &kTraySeparatorMenuItem, &kTrayExitMenuItem}) {
    RECT item_rect = TrayMenuItemRect(window, *item);
    if (item->separator) {
      RECT divider = item_rect;
      const int inset = ScaleForWindowDpi(window, kTrayMenuDividerInset);
      const int divider_height =
          ScaleForWindowDpi(window, kTrayMenuDividerHeight);
      divider.left += inset;
      divider.right -= inset;
      divider.top =
          item_rect.top + (item_rect.bottom - item_rect.top - divider_height) /
                              2;
      divider.bottom = divider.top + divider_height;
      HBRUSH accent = CreateSolidBrush(RGB(82, 160, 124));
      FillRect(memory_dc, &divider, accent);
      DeleteObject(accent);
      continue;
    }

    if (state && state->hovered_command == item->command) {
      HBRUSH selected = CreateSolidBrush(RGB(232, 245, 238));
      FillRect(memory_dc, &item_rect, selected);
      DeleteObject(selected);
    }

    const int horizontal_padding =
        ScaleForWindowDpi(window, kTrayMenuHorizontalPadding);
    RECT text_rect = item_rect;
    text_rect.left += horizontal_padding;
    text_rect.right -= horizontal_padding;

    HFONT font = CreateFontW(
        -ScaleForWindowDpi(window, kTrayMenuFontSize), 0, 0, 0, FW_NORMAL,
        FALSE, FALSE, FALSE, DEFAULT_CHARSET, OUT_DEFAULT_PRECIS,
        CLIP_DEFAULT_PRECIS, CLEARTYPE_QUALITY, DEFAULT_PITCH | FF_DONTCARE,
        L"Microsoft YaHei UI");
    HFONT old_font = nullptr;
    if (font) {
      old_font = static_cast<HFONT>(SelectObject(memory_dc, font));
    }
    SetBkMode(memory_dc, TRANSPARENT);
    SetTextColor(memory_dc, RGB(0, 0, 0));
    DrawTextW(memory_dc, item->label, -1, &text_rect,
              DT_SINGLELINE | DT_VCENTER | DT_END_ELLIPSIS | DT_NOPREFIX);
    if (old_font) {
      SelectObject(memory_dc, old_font);
    }
    if (font) {
      DeleteObject(font);
    }
  }

  SelectClipRgn(memory_dc, nullptr);
  HPEN border = CreatePen(PS_SOLID, ScaleForWindowDpi(window, 1),
                          RGB(210, 216, 224));
  HBRUSH hollow = static_cast<HBRUSH>(GetStockObject(HOLLOW_BRUSH));
  old_pen = static_cast<HPEN>(SelectObject(memory_dc, border));
  old_brush = static_cast<HBRUSH>(SelectObject(memory_dc, hollow));
  RoundRect(memory_dc, 0, 0, width, height, diameter, diameter);
  SelectObject(memory_dc, old_brush);
  SelectObject(memory_dc, old_pen);
  DeleteObject(border);
  DeleteObject(white);

  BitBlt(hdc, 0, 0, width, height, memory_dc, 0, 0, SRCCOPY);
  RestoreDC(memory_dc, saved);
  SelectObject(memory_dc, old_bitmap);
  DeleteObject(bitmap);
  DeleteDC(memory_dc);
}

LRESULT CALLBACK TrayPopupMenuWindowProc(HWND window, UINT message,
                                         WPARAM wparam, LPARAM lparam) {
  auto* state = reinterpret_cast<TrayPopupMenuState*>(
      GetWindowLongPtrW(window, GWLP_USERDATA));

  switch (message) {
    case WM_NCCREATE: {
      const auto* create = reinterpret_cast<const CREATESTRUCTW*>(lparam);
      SetWindowLongPtrW(window, GWLP_USERDATA,
                        reinterpret_cast<LONG_PTR>(create->lpCreateParams));
      return TRUE;
    }
    case WM_ERASEBKGND:
      return TRUE;
    case WM_PAINT: {
      PAINTSTRUCT paint{};
      HDC hdc = BeginPaint(window, &paint);
      DrawTrayPopupMenu(window, state, hdc);
      EndPaint(window, &paint);
      return 0;
    }
    case WM_MOUSEMOVE: {
      if (!state) {
        return 0;
      }
      POINT point{GET_X_LPARAM(lparam), GET_Y_LPARAM(lparam)};
      const TrayMenuItem* item = TrayMenuItemAtPoint(window, point);
      const UINT next_hovered = item ? item->command : 0;
      if (state->hovered_command != next_hovered) {
        state->hovered_command = next_hovered;
        InvalidateRect(window, nullptr, FALSE);
      }
      return 0;
    }
    case WM_LBUTTONUP: {
      POINT point{GET_X_LPARAM(lparam), GET_Y_LPARAM(lparam)};
      const TrayMenuItem* item = TrayMenuItemAtPoint(window, point);
      const HWND owner = state ? state->owner : nullptr;
      DestroyWindow(window);
      if (owner && item) {
        PostMessageW(owner, kTrayMenuCommandMessage, item->command, 0);
      }
      return 0;
    }
    case WM_RBUTTONUP:
    case WM_MBUTTONUP:
    case WM_CANCELMODE:
      DestroyWindow(window);
      return 0;
    case WM_KEYDOWN:
      if (wparam == VK_ESCAPE) {
        DestroyWindow(window);
        return 0;
      }
      break;
    case WM_ACTIVATE:
      if (LOWORD(wparam) == WA_INACTIVE) {
        DestroyWindow(window);
        return 0;
      }
      break;
    case WM_NCDESTROY:
      if (GetCapture() == window) {
        ReleaseCapture();
      }
      delete state;
      SetWindowLongPtrW(window, GWLP_USERDATA, 0);
      return 0;
  }

  return DefWindowProcW(window, message, wparam, lparam);
}

bool RegisterTrayPopupMenuClass(HINSTANCE instance) {
  WNDCLASSEXW window_class{};
  window_class.cbSize = sizeof(window_class);
  window_class.style = CS_DROPSHADOW | CS_HREDRAW | CS_VREDRAW;
  window_class.lpfnWndProc = TrayPopupMenuWindowProc;
  window_class.hInstance = instance;
  window_class.hCursor = LoadCursor(nullptr, IDC_ARROW);
  window_class.lpszClassName = kTrayPopupMenuClassName;

  if (RegisterClassExW(&window_class) != 0) {
    return true;
  }
  return GetLastError() == ERROR_CLASS_ALREADY_EXISTS;
}

bool ShowTrayPopupMenu(HWND owner) {
  HINSTANCE instance = GetModuleHandle(nullptr);
  if (!RegisterTrayPopupMenuClass(instance)) {
    return false;
  }

  const int width = TrayMenuPopupWidth(owner);
  const int height = TrayMenuPopupHeight(owner);
  POINT cursor{};
  GetCursorPos(&cursor);

  MONITORINFO monitor{};
  monitor.cbSize = sizeof(monitor);
  HMONITOR nearest_monitor = MonitorFromPoint(cursor, MONITOR_DEFAULTTONEAREST);
  GetMonitorInfoW(nearest_monitor, &monitor);
  RECT work_area = monitor.rcWork;
  int x = cursor.x;
  int y = cursor.y;
  if (x + width > work_area.right) {
    x = work_area.right - width;
  }
  if (y + height > work_area.bottom) {
    y = cursor.y - height;
  }
  if (x < work_area.left) {
    x = work_area.left;
  }
  if (y < work_area.top) {
    y = work_area.top;
  }

  auto* state = new TrayPopupMenuState(owner);
  HWND popup = CreateWindowExW(
      WS_EX_TOPMOST | WS_EX_TOOLWINDOW, kTrayPopupMenuClassName, L"",
      WS_POPUP, x, y, width, height, owner, nullptr, instance, state);
  if (!popup) {
    delete state;
    return false;
  }

  const int radius = ScaleForWindowDpi(popup, kTrayMenuCornerRadius);
  HRGN region =
      CreateRoundRectRgn(0, 0, width + 1, height + 1, radius * 2, radius * 2);
  if (region && SetWindowRgn(popup, region, FALSE) == 0) {
    DeleteObject(region);
  }

  ShowWindow(popup, SW_SHOWNORMAL);
  UpdateWindow(popup);
  SetForegroundWindow(popup);
  SetFocus(popup);
  SetCapture(popup);
  return true;
}

class ScopedComInitialization {
 public:
  ScopedComInitialization() : ok_(EnsureComInitialized(&initialized_here_)) {}

  ~ScopedComInitialization() {
    if (initialized_here_) {
      CoUninitialize();
    }
  }

  bool ok() const { return ok_; }

 private:
  bool initialized_here_ = false;
  bool ok_ = false;
};

struct AudioDeviceChange {
  AudioDeviceChange(EDataFlow flow, std::string device_id)
      : flow(flow), device_id(std::move(device_id)) {}

  EDataFlow flow;
  std::string device_id;
};

class AudioDeviceNotificationClient final : public IMMNotificationClient {
 public:
  explicit AudioDeviceNotificationClient(
      std::function<void(EDataFlow, std::string)> on_default_changed)
      : on_default_changed_(std::move(on_default_changed)) {}

  ULONG STDMETHODCALLTYPE AddRef() override {
    return static_cast<ULONG>(InterlockedIncrement(&ref_count_));
  }

  ULONG STDMETHODCALLTYPE Release() override {
    const LONG count = InterlockedDecrement(&ref_count_);
    if (count == 0) {
      delete this;
    }
    return static_cast<ULONG>(count);
  }

  HRESULT STDMETHODCALLTYPE QueryInterface(REFIID riid, void** object) override {
    if (!object) {
      return E_POINTER;
    }
    if (riid == __uuidof(IUnknown) ||
        riid == __uuidof(IMMNotificationClient)) {
      *object = static_cast<IMMNotificationClient*>(this);
      AddRef();
      return S_OK;
    }
    *object = nullptr;
    return E_NOINTERFACE;
  }

  HRESULT STDMETHODCALLTYPE OnDefaultDeviceChanged(
      EDataFlow flow,
      ERole role,
      LPCWSTR default_device_id) override {
    if (role == eConsole && (flow == eCapture || flow == eRender)) {
      on_default_changed_(flow, WideToUtf8(default_device_id));
    }
    return S_OK;
  }

  HRESULT STDMETHODCALLTYPE OnDeviceAdded(LPCWSTR device_id) override {
    return S_OK;
  }

  HRESULT STDMETHODCALLTYPE OnDeviceRemoved(LPCWSTR device_id) override {
    return S_OK;
  }

  HRESULT STDMETHODCALLTYPE OnDeviceStateChanged(LPCWSTR device_id,
                                                 DWORD new_state) override {
    return S_OK;
  }

  HRESULT STDMETHODCALLTYPE OnPropertyValueChanged(
      LPCWSTR device_id,
      const PROPERTYKEY key) override {
    return S_OK;
  }

 private:
  volatile LONG ref_count_ = 1;
  std::function<void(EDataFlow, std::string)> on_default_changed_;
};

std::optional<std::string> AudioEndpointId(IMMDevice* device) {
  if (!device) {
    return std::nullopt;
  }
  wchar_t* raw_id = nullptr;
  if (FAILED(device->GetId(&raw_id)) || !raw_id) {
    return std::nullopt;
  }
  const std::string device_id = WideToUtf8(raw_id);
  CoTaskMemFree(raw_id);
  if (device_id.empty()) {
    return std::nullopt;
  }
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

std::optional<std::string> DefaultAudioEndpointIdFromEnumerator(
    IMMDeviceEnumerator* enumerator,
    EDataFlow flow) {
  if (!enumerator) {
    return std::nullopt;
  }

  IMMDevice* device = nullptr;
  std::optional<std::string> result;
  if (SUCCEEDED(enumerator->GetDefaultAudioEndpoint(flow, eConsole, &device))) {
    result = AudioEndpointId(device);
  }
  SafeRelease(device);
  return result;
}

std::optional<std::string> DefaultAudioEndpointId(EDataFlow flow) {
  ScopedComInitialization com;
  if (!com.ok()) {
    return std::nullopt;
  }

  IMMDeviceEnumerator* enumerator = nullptr;
  std::optional<std::string> result;
  if (SUCCEEDED(CoCreateInstance(__uuidof(MMDeviceEnumerator), nullptr,
                                 CLSCTX_ALL, IID_PPV_ARGS(&enumerator)))) {
    result = DefaultAudioEndpointIdFromEnumerator(enumerator, flow);
  }
  SafeRelease(enumerator);
  return result;
}

flutter::EncodableValue NullableStringValue(
    const std::optional<std::string>& value) {
  return value && !value->empty() ? flutter::EncodableValue(*value)
                                  : flutter::EncodableValue();
}

flutter::EncodableList EnumerateAudioEndpoints(EDataFlow flow,
                                               const std::string& fallback) {
  flutter::EncodableList devices;
  ScopedComInitialization com;
  if (!com.ok()) {
    return devices;
  }

  IMMDeviceEnumerator* enumerator = nullptr;
  IMMDeviceCollection* collection = nullptr;
  if (FAILED(CoCreateInstance(__uuidof(MMDeviceEnumerator), nullptr,
                              CLSCTX_ALL, IID_PPV_ARGS(&enumerator))) ||
      FAILED(enumerator->EnumAudioEndpoints(flow, DEVICE_STATE_ACTIVE,
                                            &collection))) {
    SafeRelease(collection);
    SafeRelease(enumerator);
    return devices;
  }

  const auto default_id = DefaultAudioEndpointIdFromEnumerator(enumerator, flow);
  UINT count = 0;
  if (SUCCEEDED(collection->GetCount(&count))) {
    for (UINT i = 0; i < count; ++i) {
      IMMDevice* device = nullptr;
      if (FAILED(collection->Item(i, &device))) {
        continue;
      }
      const auto device_id = AudioEndpointId(device);
      if (device_id) {
        flutter::EncodableMap entry;
        entry[flutter::EncodableValue("deviceId")] =
            flutter::EncodableValue(*device_id);
        entry[flutter::EncodableValue("label")] = flutter::EncodableValue(
            AudioEndpointFriendlyName(device, fallback + " " + *device_id));
        entry[flutter::EncodableValue("isDefault")] =
            flutter::EncodableValue(default_id && *default_id == *device_id);
        devices.emplace_back(entry);
      }
      SafeRelease(device);
    }
  }

  SafeRelease(collection);
  SafeRelease(enumerator);
  return devices;
}

size_t DibPixelOffset(const BITMAPINFOHEADER* header, size_t total_size) {
  if (!header || header->biSize < sizeof(BITMAPINFOHEADER) ||
      header->biSize > total_size) {
    return 0;
  }

  size_t offset = header->biSize;
  if (header->biSize == sizeof(BITMAPINFOHEADER) &&
      (header->biCompression == BI_BITFIELDS ||
       header->biCompression == kBiAlphaBitFields)) {
    offset += header->biCompression == kBiAlphaBitFields
                  ? 4 * sizeof(DWORD)
                  : 3 * sizeof(DWORD);
  }

  if (header->biBitCount <= 8) {
    const DWORD color_count =
        header->biClrUsed ? header->biClrUsed : (1u << header->biBitCount);
    offset += static_cast<size_t>(color_count) * sizeof(RGBQUAD);
  }

  return offset <= total_size ? offset : 0;
}

HGLOBAL CreateBmpGlobalFromDib(HGLOBAL dib_handle) {
  if (!dib_handle) {
    return nullptr;
  }

  const SIZE_T dib_size = GlobalSize(dib_handle);
  if (dib_size == 0) {
    return nullptr;
  }

  void* dib_data = GlobalLock(dib_handle);
  if (!dib_data) {
    return nullptr;
  }

  const auto* header = reinterpret_cast<const BITMAPINFOHEADER*>(dib_data);
  const size_t pixel_offset = DibPixelOffset(header, dib_size);
  if (pixel_offset == 0) {
    GlobalUnlock(dib_handle);
    return nullptr;
  }

  const SIZE_T bmp_size = sizeof(BITMAPFILEHEADER) + dib_size;
  HGLOBAL bmp_handle = GlobalAlloc(GMEM_MOVEABLE, bmp_size);
  if (!bmp_handle) {
    GlobalUnlock(dib_handle);
    return nullptr;
  }

  void* bmp_data = GlobalLock(bmp_handle);
  if (!bmp_data) {
    GlobalFree(bmp_handle);
    GlobalUnlock(dib_handle);
    return nullptr;
  }

  BITMAPFILEHEADER file_header{};
  file_header.bfType = 0x4D42;
  file_header.bfSize = static_cast<DWORD>(bmp_size);
  file_header.bfOffBits =
      static_cast<DWORD>(sizeof(BITMAPFILEHEADER) + pixel_offset);

  std::memcpy(bmp_data, &file_header, sizeof(file_header));
  std::memcpy(static_cast<uint8_t*>(bmp_data) + sizeof(file_header), dib_data,
              dib_size);

  GlobalUnlock(bmp_handle);
  GlobalUnlock(dib_handle);
  return bmp_handle;
}

std::optional<std::vector<uint8_t>> StreamBytes(IStream* stream) {
  if (!stream) {
    return std::nullopt;
  }

  HGLOBAL global = nullptr;
  if (FAILED(GetHGlobalFromStream(stream, &global)) || !global) {
    return std::nullopt;
  }

  const SIZE_T size = GlobalSize(global);
  if (size == 0) {
    return std::nullopt;
  }

  void* data = GlobalLock(global);
  if (!data) {
    return std::nullopt;
  }

  std::vector<uint8_t> bytes(size);
  std::memcpy(bytes.data(), data, size);
  GlobalUnlock(global);
  return bytes;
}

std::optional<std::vector<uint8_t>> EncodeBitmapSourceToPng(
    IWICImagingFactory* factory,
    IWICBitmapSource* source) {
  if (!factory || !source) {
    return std::nullopt;
  }

  IWICFormatConverter* converter = nullptr;
  IWICBitmapSource* source_to_write = source;
  if (SUCCEEDED(factory->CreateFormatConverter(&converter)) &&
      SUCCEEDED(converter->Initialize(source, GUID_WICPixelFormat32bppBGRA,
                                      WICBitmapDitherTypeNone, nullptr, 0.0,
                                      WICBitmapPaletteTypeCustom))) {
    source_to_write = converter;
  }

  UINT width = 0;
  UINT height = 0;
  if (FAILED(source_to_write->GetSize(&width, &height)) || width == 0 ||
      height == 0) {
    SafeRelease(converter);
    return std::nullopt;
  }

  IStream* output_stream = nullptr;
  IWICBitmapEncoder* encoder = nullptr;
  IWICBitmapFrameEncode* frame = nullptr;
  IPropertyBag2* properties = nullptr;
  std::optional<std::vector<uint8_t>> result;

  if (SUCCEEDED(CreateStreamOnHGlobal(nullptr, TRUE, &output_stream)) &&
      SUCCEEDED(factory->CreateEncoder(GUID_ContainerFormatPng, nullptr,
                                       &encoder)) &&
      SUCCEEDED(
          encoder->Initialize(output_stream, WICBitmapEncoderNoCache)) &&
      SUCCEEDED(encoder->CreateNewFrame(&frame, &properties)) &&
      SUCCEEDED(frame->Initialize(properties)) &&
      SUCCEEDED(frame->SetSize(width, height))) {
    WICPixelFormatGUID pixel_format = GUID_WICPixelFormat32bppBGRA;
    if (SUCCEEDED(frame->SetPixelFormat(&pixel_format)) &&
        SUCCEEDED(frame->WriteSource(source_to_write, nullptr)) &&
        SUCCEEDED(frame->Commit()) && SUCCEEDED(encoder->Commit())) {
      result = StreamBytes(output_stream);
    }
  }

  SafeRelease(properties);
  SafeRelease(frame);
  SafeRelease(encoder);
  SafeRelease(output_stream);
  SafeRelease(converter);
  return result;
}

std::optional<std::vector<uint8_t>> EncodeDibToPng(
    IWICImagingFactory* factory,
    HGLOBAL dib_handle) {
  HGLOBAL bmp_handle = CreateBmpGlobalFromDib(dib_handle);
  if (!bmp_handle) {
    return std::nullopt;
  }

  IStream* bmp_stream = nullptr;
  IWICBitmapDecoder* decoder = nullptr;
  IWICBitmapFrameDecode* frame = nullptr;
  std::optional<std::vector<uint8_t>> result;

  if (SUCCEEDED(CreateStreamOnHGlobal(bmp_handle, TRUE, &bmp_stream))) {
    if (SUCCEEDED(factory->CreateDecoderFromStream(
            bmp_stream, nullptr, WICDecodeMetadataCacheOnLoad, &decoder)) &&
        SUCCEEDED(decoder->GetFrame(0, &frame))) {
      result = EncodeBitmapSourceToPng(factory, frame);
    }
  } else {
    GlobalFree(bmp_handle);
  }

  SafeRelease(frame);
  SafeRelease(decoder);
  SafeRelease(bmp_stream);
  return result;
}

std::optional<std::vector<uint8_t>> EncodeHBitmapToPng(
    IWICImagingFactory* factory,
    HBITMAP bitmap) {
  if (!factory || !bitmap) {
    return std::nullopt;
  }

  IWICBitmap* source = nullptr;
  std::optional<std::vector<uint8_t>> result;
  if (SUCCEEDED(factory->CreateBitmapFromHBITMAP(
          bitmap, nullptr, WICBitmapIgnoreAlpha, &source))) {
    result = EncodeBitmapSourceToPng(factory, source);
  }
  SafeRelease(source);
  return result;
}

std::optional<std::vector<uint8_t>> ReadClipboardImagePng(HWND owner) {
  if (!IsClipboardFormatAvailable(CF_DIBV5) &&
      !IsClipboardFormatAvailable(CF_DIB) &&
      !IsClipboardFormatAvailable(CF_BITMAP)) {
    return std::nullopt;
  }
  if (!OpenClipboard(owner)) {
    return std::nullopt;
  }

  bool com_initialized_here = false;
  IWICImagingFactory* factory = nullptr;
  std::optional<std::vector<uint8_t>> result;
  if (EnsureComInitialized(&com_initialized_here) &&
      SUCCEEDED(CoCreateInstance(CLSID_WICImagingFactory, nullptr,
                                 CLSCTX_INPROC_SERVER,
                                 IID_PPV_ARGS(&factory)))) {
    if (IsClipboardFormatAvailable(CF_DIBV5)) {
      result = EncodeDibToPng(
          factory, static_cast<HGLOBAL>(GetClipboardData(CF_DIBV5)));
    }
    if (!result && IsClipboardFormatAvailable(CF_DIB)) {
      result = EncodeDibToPng(
          factory, static_cast<HGLOBAL>(GetClipboardData(CF_DIB)));
    }
    if (!result && IsClipboardFormatAvailable(CF_BITMAP)) {
      result = EncodeHBitmapToPng(
          factory, static_cast<HBITMAP>(GetClipboardData(CF_BITMAP)));
    }
  }

  SafeRelease(factory);
  CloseClipboard();
  if (com_initialized_here) {
    CoUninitialize();
  }
  return result;
}

// Writes encoded image bytes (PNG/JPEG/etc., anything WIC can decode) onto the
// clipboard as a CF_DIB so other apps can paste it. Returns false on malformed
// input or if the clipboard can't be opened.
bool WriteClipboardImage(HWND owner, const std::vector<uint8_t>& bytes) {
  if (bytes.empty()) {
    return false;
  }

  bool com_initialized_here = false;
  if (!EnsureComInitialized(&com_initialized_here)) {
    return false;
  }

  IWICImagingFactory* factory = nullptr;
  IStream* input_stream = nullptr;
  IWICBitmapDecoder* decoder = nullptr;
  IWICBitmapFrameDecode* frame = nullptr;
  IWICFormatConverter* converter = nullptr;
  HGLOBAL dib = nullptr;
  bool success = false;

  // Build a packed DIB (BITMAPINFOHEADER + 32bpp BGRA pixels, top-down) in a
  // movable global the clipboard takes ownership of on success.
  if (SUCCEEDED(CoCreateInstance(CLSID_WICImagingFactory, nullptr,
                                 CLSCTX_INPROC_SERVER,
                                 IID_PPV_ARGS(&factory))) &&
      SUCCEEDED(CreateStreamOnHGlobal(nullptr, TRUE, &input_stream))) {
    ULONG written = 0;
    if (SUCCEEDED(input_stream->Write(bytes.data(),
                                      static_cast<ULONG>(bytes.size()),
                                      &written)) &&
        written == bytes.size()) {
      LARGE_INTEGER zero = {};
      input_stream->Seek(zero, STREAM_SEEK_SET, nullptr);
      if (SUCCEEDED(factory->CreateDecoderFromStream(
              input_stream, nullptr, WICDecodeMetadataCacheOnLoad, &decoder)) &&
          SUCCEEDED(decoder->GetFrame(0, &frame)) &&
          SUCCEEDED(factory->CreateFormatConverter(&converter)) &&
          SUCCEEDED(converter->Initialize(
              frame, GUID_WICPixelFormat32bppBGRA, WICBitmapDitherTypeNone,
              nullptr, 0.0, WICBitmapPaletteTypeCustom))) {
        UINT width = 0;
        UINT height = 0;
        if (SUCCEEDED(converter->GetSize(&width, &height)) && width > 0 &&
            height > 0) {
          const UINT stride = width * 4;
          const UINT pixels_size = stride * height;
          const SIZE_T dib_size = sizeof(BITMAPINFOHEADER) + pixels_size;
          dib = GlobalAlloc(GMEM_MOVEABLE, dib_size);
          if (dib) {
            void* dib_data = GlobalLock(dib);
            if (dib_data) {
              BITMAPINFOHEADER* header =
                  static_cast<BITMAPINFOHEADER*>(dib_data);
              ZeroMemory(header, sizeof(BITMAPINFOHEADER));
              header->biSize = sizeof(BITMAPINFOHEADER);
              header->biWidth = static_cast<LONG>(width);
              // Negative height => top-down rows, matching WIC's layout.
              header->biHeight = -static_cast<LONG>(height);
              header->biPlanes = 1;
              header->biBitCount = 32;
              header->biCompression = BI_RGB;
              header->biSizeImage = pixels_size;
              BYTE* pixels =
                  static_cast<BYTE*>(dib_data) + sizeof(BITMAPINFOHEADER);
              WICRect rect = {0, 0, static_cast<INT>(width),
                              static_cast<INT>(height)};
              if (SUCCEEDED(converter->CopyPixels(&rect, stride, pixels_size,
                                                  pixels))) {
                success = true;
              }
              GlobalUnlock(dib);
            }
          }
        }
      }
    }
  }

  if (success && OpenClipboard(owner)) {
    if (EmptyClipboard() && SetClipboardData(CF_DIB, dib)) {
      // Clipboard now owns the global; don't free it below.
      dib = nullptr;
    } else {
      success = false;
    }
    CloseClipboard();
  } else {
    success = false;
  }

  if (dib) {
    GlobalFree(dib);
  }
  SafeRelease(converter);
  SafeRelease(frame);
  SafeRelease(decoder);
  SafeRelease(input_stream);
  SafeRelease(factory);
  if (com_initialized_here) {
    CoUninitialize();
  }
  return success;
}

flutter::EncodableList ReadDropFilePaths(HDROP drop) {
  flutter::EncodableList paths;
  if (!drop) {
    return paths;
  }

  const UINT count = DragQueryFileW(drop, 0xFFFFFFFF, nullptr, 0);
  for (UINT i = 0; i < count; ++i) {
    const UINT length = DragQueryFileW(drop, i, nullptr, 0);
    if (length == 0) {
      continue;
    }

    std::vector<wchar_t> buffer(length + 1);
    if (DragQueryFileW(drop, i, buffer.data(),
                       static_cast<UINT>(buffer.size())) == 0) {
      continue;
    }

    const std::string path = WideToUtf8(buffer.data());
    if (!path.empty()) {
      paths.emplace_back(path);
    }
  }
  return paths;
}

double LogicalDropCoordinate(HWND window, LONG value) {
  const UINT dpi = GetDpiForWindow(window);
  const double scale = dpi > 0 ? static_cast<double>(dpi) / 96.0 : 1.0;
  return static_cast<double>(value) / scale;
}

LRESULT CALLBACK FileDropChildProc(HWND window, UINT const message,
                                   WPARAM const wparam,
                                   LPARAM const lparam) noexcept {
  auto flutter_window =
      reinterpret_cast<FlutterWindow*>(GetPropW(window, kFileDropWindowProp));
  if (flutter_window && message == WM_DROPFILES) {
    flutter_window->HandleNativeFileDrop(
        window, reinterpret_cast<HDROP>(wparam));
    return 0;
  }

  WNDPROC original_proc = reinterpret_cast<WNDPROC>(
      GetPropW(window, kFileDropOriginalProcProp));
  if (original_proc) {
    return CallWindowProc(original_proc, window, message, wparam, lparam);
  }
  return DefWindowProc(window, message, wparam, lparam);
}

flutter::EncodableList ReadClipboardFilePaths(HWND owner) {
  flutter::EncodableList paths;
  if (!IsClipboardFormatAvailable(CF_HDROP) || !OpenClipboard(owner)) {
    return paths;
  }

  const HANDLE data = GetClipboardData(CF_HDROP);
  if (data) {
    paths = ReadDropFilePaths(static_cast<HDROP>(data));
  }

  CloseClipboard();
  return paths;
}

bool WriteClipboardFilePaths(HWND owner, const std::vector<std::string>& paths) {
  std::vector<std::wstring> wide_paths;
  size_t char_count = 1;  // Final extra NUL for CF_HDROP.
  for (const auto& path : paths) {
    auto wide = Utf8ToWide(path);
    if (wide.empty()) {
      continue;
    }
    char_count += wide.size() + 1;
    wide_paths.push_back(std::move(wide));
  }
  if (wide_paths.empty()) {
    return false;
  }

  const SIZE_T bytes = sizeof(DROPFILES) + char_count * sizeof(wchar_t);
  HGLOBAL global = GlobalAlloc(GMEM_MOVEABLE | GMEM_ZEROINIT, bytes);
  if (!global) {
    return false;
  }

  auto* drop = static_cast<DROPFILES*>(GlobalLock(global));
  if (!drop) {
    GlobalFree(global);
    return false;
  }
  drop->pFiles = sizeof(DROPFILES);
  drop->fWide = TRUE;
  auto* cursor = reinterpret_cast<wchar_t*>(
      reinterpret_cast<BYTE*>(drop) + sizeof(DROPFILES));
  for (const auto& path : wide_paths) {
    std::wmemcpy(cursor, path.c_str(), path.size());
    cursor += path.size() + 1;
  }
  *cursor = L'\0';
  GlobalUnlock(global);

  if (!OpenClipboard(owner)) {
    GlobalFree(global);
    return false;
  }
  bool success = false;
  if (EmptyClipboard() && SetClipboardData(CF_HDROP, global)) {
    success = true;
    global = nullptr;  // Clipboard owns the memory after SetClipboardData.
  }
  CloseClipboard();
  if (global) {
    GlobalFree(global);
  }
  return success;
}

}  // namespace

FlutterWindow::FlutterWindow(const flutter::DartProject& project)
    : project_(project) {}

FlutterWindow::~FlutterWindow() {}

void FlutterWindow::RegisterAudioDevicesChannel() {
  audio_devices_channel_ =
      std::make_unique<flutter::MethodChannel<flutter::EncodableValue>>(
          flutter_controller_->engine()->messenger(), kAudioDevicesChannelName,
          &flutter::StandardMethodCodec::GetInstance());
  audio_devices_channel_->SetMethodCallHandler(
      [this](
          const flutter::MethodCall<flutter::EncodableValue>& call,
          std::unique_ptr<flutter::MethodResult<flutter::EncodableValue>>
              result) {
        if (call.method_name() == kEnumerateInputsMethod) {
          result->Success(EnumerateAudioEndpoints(eCapture, "Microphone"));
          return;
        }
        if (call.method_name() == kEnumerateOutputsMethod) {
          result->Success(EnumerateAudioEndpoints(eRender, "Speaker"));
          return;
        }
        if (call.method_name() == kDefaultInputDeviceIdMethod) {
          result->Success(NullableStringValue(DefaultAudioEndpointId(eCapture)));
          return;
        }
        if (call.method_name() == kDefaultOutputDeviceIdMethod) {
          result->Success(NullableStringValue(DefaultAudioEndpointId(eRender)));
          return;
        }
        if (call.method_name() == kStartListeningMethod) {
          EnsureAudioDeviceNotifications();
          result->Success(flutter::EncodableValue());
          return;
        }
        result->NotImplemented();
      });
}

void FlutterWindow::RegisterTrayChannel() {
  tray_channel_ =
      std::make_unique<flutter::MethodChannel<flutter::EncodableValue>>(
          flutter_controller_->engine()->messenger(), kTrayChannelName,
          &flutter::StandardMethodCodec::GetInstance());
  tray_channel_->SetMethodCallHandler(
      [this](
          const flutter::MethodCall<flutter::EncodableValue>& call,
          std::unique_ptr<flutter::MethodResult<flutter::EncodableValue>>
              result) {
        if (call.method_name() == kTrayInitializeMethod) {
          result->Success(flutter::EncodableValue(ShowTrayIcon()));
          return;
        }
        if (call.method_name() == kTrayDisposeMethod) {
          RemoveTrayIcon();
          result->Success(flutter::EncodableValue());
          return;
        }
        if (call.method_name() == kTrayRequestAttentionMethod) {
          RequestMessageAttention();
          result->Success(flutter::EncodableValue());
          return;
        }
        if (call.method_name() == kSetMediaPlaybackActiveMethod) {
          const auto* active = std::get_if<bool>(call.arguments());
          if (!active) {
            result->Error("invalid_arguments", "Expected an active boolean");
            return;
          }
          if (!SetMediaPlaybackActive(*active)) {
            result->Error("power_request_failed",
                          "SetThreadExecutionState failed");
            return;
          }
          result->Success(flutter::EncodableValue());
          return;
        }
        result->NotImplemented();
      });
}

bool FlutterWindow::SetMediaPlaybackActive(bool active) {
  EXECUTION_STATE flags = ES_CONTINUOUS;
  if (active) {
    flags = static_cast<EXECUTION_STATE>(
        flags | ES_DISPLAY_REQUIRED | ES_SYSTEM_REQUIRED);
  }
  if (SetThreadExecutionState(flags) == 0) {
    return false;
  }
  media_playback_active_ = active;
  return true;
}

bool FlutterWindow::ShowTrayIcon() {
  NOTIFYICONDATAW data{};
  data.cbSize = sizeof(data);
  data.hWnd = GetHandle();
  data.uID = kTrayIconId;
  data.uFlags = NIF_MESSAGE | NIF_ICON | NIF_TIP;
  data.uCallbackMessage = kTrayCallbackMessage;
  data.hIcon = LoadIcon(GetModuleHandle(nullptr), MAKEINTRESOURCE(IDI_APP_ICON));
  wcscpy_s(data.szTip, L"Gang Chat");

  const DWORD action = tray_icon_added_ ? NIM_MODIFY : NIM_ADD;
  if (!Shell_NotifyIconW(action, &data)) {
    return false;
  }
  tray_icon_added_ = true;
  return true;
}

void FlutterWindow::RemoveTrayIcon() {
  StopMessageAttention();
  if (!tray_icon_added_) {
    if (tray_attention_blank_icon_) {
      DestroyIcon(tray_attention_blank_icon_);
      tray_attention_blank_icon_ = nullptr;
    }
    return;
  }
  NOTIFYICONDATAW data{};
  data.cbSize = sizeof(data);
  data.hWnd = GetHandle();
  data.uID = kTrayIconId;
  Shell_NotifyIconW(NIM_DELETE, &data);
  tray_icon_added_ = false;
  if (tray_attention_blank_icon_) {
    DestroyIcon(tray_attention_blank_icon_);
    tray_attention_blank_icon_ = nullptr;
  }
}

void FlutterWindow::RequestMessageAttention() {
  const HWND window = GetHandle();
  if (!window) {
    return;
  }
  const bool window_is_foreground =
      IsWindowVisible(window) && GetForegroundWindow() == window;
  if (window_is_foreground) {
    StopMessageAttention();
    return;
  }

  // Keep the notification-area cue active alongside the taskbar cue. The tray
  // icon is created lazily so normal startup behavior remains unchanged until
  // an unread message actually needs attention.
  if (!tray_icon_added_) {
    ShowTrayIcon();
  }

  // FlashWindow(TRUE) changes the inactive taskbar button to its attention
  // state once and leaves it highlighted. Repeated messages must not call it
  // again because a second inversion would remove the highlight.
  if (IsWindowVisible(window) && !taskbar_attention_active_) {
    FlashWindow(window, TRUE);
    taskbar_attention_active_ = true;
  }

  StartMessageAttentionTimer();
}

void FlutterWindow::StartMessageAttentionTimer() {
  if (attention_timer_active_) {
    return;
  }
  if (tray_icon_added_ && !tray_attention_blank_icon_) {
    tray_attention_blank_icon_ = CreateTransparentTrayIcon();
  }
  const bool can_flash_tray =
      tray_icon_added_ && tray_attention_blank_icon_ != nullptr;
  if (!taskbar_attention_active_ && !can_flash_tray) {
    return;
  }
  tray_attention_icon_visible_ = true;
  attention_timer_active_ =
      SetTimer(GetHandle(), kMessageAttentionTimerId,
               kMessageAttentionIntervalMs, nullptr) != 0;
}

void FlutterWindow::ScheduleMessageAttentionStop() {
  if (attention_stop_posted_ ||
      (!attention_timer_active_ && !taskbar_attention_active_)) {
    return;
  }
  // Defer native icon updates until the activation message has returned. This
  // keeps the response effectively immediate without re-entering Flutter's
  // child-window focus routing from WM_ACTIVATE.
  attention_stop_posted_ =
      PostMessage(GetHandle(), kStopMessageAttentionMessage, 0, 0) != FALSE;
}

void FlutterWindow::StopMessageAttention() {
  if (attention_timer_active_) {
    KillTimer(GetHandle(), kMessageAttentionTimerId);
  }
  attention_timer_active_ = false;
  if (taskbar_attention_active_) {
    const HWND window = GetHandle();
    // Windows clears the attention state itself when this window becomes
    // foreground. Calling FlashWindow(FALSE) after that transition causes one
    // extra visible taskbar flash, so only clear it explicitly while another
    // window still owns the foreground.
    if (window && GetForegroundWindow() != window) {
      FlashWindow(window, FALSE);
    }
    taskbar_attention_active_ = false;
  }
  if (!tray_attention_icon_visible_ && tray_icon_added_) {
    SetTrayIconImage(
        LoadIcon(GetModuleHandle(nullptr), MAKEINTRESOURCE(IDI_APP_ICON)));
  }
  tray_attention_icon_visible_ = true;
}

void FlutterWindow::TickMessageAttention() {
  if (!attention_timer_active_) {
    return;
  }
  const HWND window = GetHandle();
  if (window && IsWindowVisible(window) && GetForegroundWindow() == window) {
    // This runs on the attention timer, after Windows has completed its focus
    // transition. Do not move this cleanup into WM_ACTIVATE or WM_SETFOCUS.
    StopMessageAttention();
    return;
  }
  if (!tray_icon_added_ || !tray_attention_blank_icon_) {
    return;
  }
  tray_attention_icon_visible_ = !tray_attention_icon_visible_;
  const HICON icon = tray_attention_icon_visible_
                         ? LoadIcon(GetModuleHandle(nullptr),
                                    MAKEINTRESOURCE(IDI_APP_ICON))
                         : tray_attention_blank_icon_;
  SetTrayIconImage(icon);
}

bool FlutterWindow::SetTrayIconImage(HICON icon) {
  if (!tray_icon_added_ || !icon) {
    return false;
  }
  NOTIFYICONDATAW data{};
  data.cbSize = sizeof(data);
  data.hWnd = GetHandle();
  data.uID = kTrayIconId;
  data.uFlags = NIF_ICON;
  data.hIcon = icon;
  return Shell_NotifyIconW(NIM_MODIFY, &data) != FALSE;
}

void FlutterWindow::ShowTrayMenu() {
  SetForegroundWindow(GetHandle());
  if (ShowTrayPopupMenu(GetHandle())) {
    return;
  }

  HMENU menu = CreatePopupMenu();
  if (!menu) {
    return;
  }
  AppendMenuW(menu, MF_STRING, kTrayOpenCommand, L"\u6253\u5f00 Gang Chat");
  AppendMenuW(menu, MF_SEPARATOR, 0, nullptr);
  AppendMenuW(menu, MF_STRING, kTrayExitCommand, L"\u9000\u51fa");

  POINT cursor{};
  GetCursorPos(&cursor);
  const UINT command = TrackPopupMenu(
      menu, TPM_RIGHTBUTTON | TPM_RETURNCMD | TPM_NONOTIFY, cursor.x,
      cursor.y, 0, GetHandle(), nullptr);
  DestroyMenu(menu);
  PostMessage(GetHandle(), WM_NULL, 0, 0);

  if (command == kTrayOpenCommand) {
    StopMessageAttention();
    InvokeTrayMethod(kTrayOpenMethod);
  } else if (command == kTrayExitCommand) {
    InvokeTrayMethod(kTrayExitMethod);
  }
}

void FlutterWindow::InvokeTrayMethod(const char* method) {
  if (!tray_channel_) {
    return;
  }
  tray_channel_->InvokeMethod(method,
                              std::make_unique<flutter::EncodableValue>());
}

bool FlutterWindow::EnsureAudioDeviceNotifications() {
  if (audio_device_enumerator_ && audio_device_notification_client_) {
    return true;
  }

  if (!audio_device_enumerator_) {
    bool initialized_here = false;
    if (!EnsureComInitialized(&initialized_here)) {
      return false;
    }
    if (initialized_here) {
      audio_com_initialized_here_ = true;
    }
    if (FAILED(CoCreateInstance(__uuidof(MMDeviceEnumerator), nullptr,
                                CLSCTX_ALL,
                                IID_PPV_ARGS(&audio_device_enumerator_)))) {
      if (initialized_here && audio_com_initialized_here_) {
        CoUninitialize();
        audio_com_initialized_here_ = false;
      }
      return false;
    }
  }

  if (!audio_device_notification_client_) {
    auto* client = new AudioDeviceNotificationClient(
        [this](EDataFlow flow, std::string device_id) {
          auto* change =
              new AudioDeviceChange(flow, std::move(device_id));
          if (!PostMessage(GetHandle(), kAudioDefaultDeviceChangedMessage, 0,
                           reinterpret_cast<LPARAM>(change))) {
            delete change;
          }
        });
    if (FAILED(audio_device_enumerator_->RegisterEndpointNotificationCallback(
            client))) {
      client->Release();
      return false;
    }
    audio_device_notification_client_ = client;
  }

  return true;
}

void FlutterWindow::DetachAudioDeviceNotifications() {
  if (audio_device_enumerator_ && audio_device_notification_client_) {
    audio_device_enumerator_->UnregisterEndpointNotificationCallback(
        audio_device_notification_client_);
  }
  SafeRelease(audio_device_notification_client_);
  SafeRelease(audio_device_enumerator_);
  if (audio_com_initialized_here_) {
    CoUninitialize();
    audio_com_initialized_here_ = false;
  }
}

void FlutterWindow::AttachFileDropTarget(HWND child_window) {
  DragAcceptFiles(GetHandle(), TRUE);
  if (!child_window) {
    return;
  }

  file_drop_child_window_ = child_window;
  DragAcceptFiles(file_drop_child_window_, TRUE);
  SetPropW(file_drop_child_window_, kFileDropWindowProp, this);
  original_child_proc_ = reinterpret_cast<WNDPROC>(SetWindowLongPtr(
      file_drop_child_window_, GWLP_WNDPROC,
      reinterpret_cast<LONG_PTR>(FileDropChildProc)));
  SetPropW(file_drop_child_window_, kFileDropOriginalProcProp,
           reinterpret_cast<HANDLE>(original_child_proc_));
}

void FlutterWindow::DetachFileDropTarget() {
  if (GetHandle()) {
    DragAcceptFiles(GetHandle(), FALSE);
  }
  if (file_drop_child_window_) {
    DragAcceptFiles(file_drop_child_window_, FALSE);
    if (original_child_proc_) {
      SetWindowLongPtr(file_drop_child_window_, GWLP_WNDPROC,
                       reinterpret_cast<LONG_PTR>(original_child_proc_));
    }
    RemovePropW(file_drop_child_window_, kFileDropWindowProp);
    RemovePropW(file_drop_child_window_, kFileDropOriginalProcProp);
  }
  file_drop_child_window_ = nullptr;
  original_child_proc_ = nullptr;
}

void FlutterWindow::HandleNativeFileDrop(HWND window, HDROP drop) {
  POINT point{};
  DragQueryPoint(drop, &point);
  flutter::EncodableList paths = ReadDropFilePaths(drop);
  DragFinish(drop);
  if (paths.empty() || !file_drop_channel_) {
    return;
  }

  flutter::EncodableMap arguments;
  arguments[flutter::EncodableValue("paths")] = flutter::EncodableValue(paths);
  arguments[flutter::EncodableValue("x")] =
      flutter::EncodableValue(LogicalDropCoordinate(window, point.x));
  arguments[flutter::EncodableValue("y")] =
      flutter::EncodableValue(LogicalDropCoordinate(window, point.y));
  file_drop_channel_->InvokeMethod(
      kDropFilesMethod, std::make_unique<flutter::EncodableValue>(arguments));
}

bool FlutterWindow::OnCreate() {
  if (!Win32Window::OnCreate()) {
    return false;
  }

  RECT frame = GetClientArea();

  // The size here must match the window dimensions to avoid unnecessary surface
  // creation / destruction in the startup path.
  flutter_controller_ = std::make_unique<flutter::FlutterViewController>(
      frame.right - frame.left, frame.bottom - frame.top, project_);
  // Ensure that basic setup of the controller was successful.
  if (!flutter_controller_->engine() || !flutter_controller_->view()) {
    return false;
  }
  auto clipboard_channel =
      std::make_unique<flutter::MethodChannel<flutter::EncodableValue>>(
          flutter_controller_->engine()->messenger(), kClipboardChannelName,
          &flutter::StandardMethodCodec::GetInstance());
  clipboard_channel->SetMethodCallHandler(
      [this](
          const flutter::MethodCall<flutter::EncodableValue>& call,
          std::unique_ptr<flutter::MethodResult<flutter::EncodableValue>>
              result) {
        if (call.method_name() == kReadFilePathsMethod) {
          result->Success(ReadClipboardFilePaths(GetHandle()));
          return;
        }
        if (call.method_name() == kReadImageFileMethod) {
          const auto bytes = ReadClipboardImagePng(GetHandle());
          if (!bytes || bytes->empty()) {
            result->Success(flutter::EncodableValue());
            return;
          }
          flutter::EncodableMap image_file;
          image_file[flutter::EncodableValue("filename")] =
              flutter::EncodableValue("clipboard-image.png");
          image_file[flutter::EncodableValue("mime_type")] =
              flutter::EncodableValue("image/png");
          image_file[flutter::EncodableValue("bytes")] =
              flutter::EncodableValue(*bytes);
          result->Success(flutter::EncodableValue(image_file));
          return;
        }
        if (call.method_name() == kWriteFilePathsMethod) {
          const auto* args =
              std::get_if<flutter::EncodableMap>(call.arguments());
          if (!args) {
            result->Success(flutter::EncodableValue(false));
            return;
          }
          const auto it = args->find(flutter::EncodableValue("paths"));
          if (it == args->end()) {
            result->Success(flutter::EncodableValue(false));
            return;
          }
          const auto* values =
              std::get_if<flutter::EncodableList>(&it->second);
          if (!values) {
            result->Success(flutter::EncodableValue(false));
            return;
          }
          std::vector<std::string> paths;
          for (const auto& value : *values) {
            if (const auto* path = std::get_if<std::string>(&value)) {
              paths.push_back(*path);
            }
          }
          const bool ok = WriteClipboardFilePaths(GetHandle(), paths);
          result->Success(flutter::EncodableValue(ok));
          return;
        }
        if (call.method_name() == kWriteImageFileMethod) {
          const auto* args =
              std::get_if<flutter::EncodableMap>(call.arguments());
          if (!args) {
            result->Success(flutter::EncodableValue(false));
            return;
          }
          const auto it = args->find(flutter::EncodableValue("bytes"));
          if (it == args->end()) {
            result->Success(flutter::EncodableValue(false));
            return;
          }
          const auto* bytes =
              std::get_if<std::vector<uint8_t>>(&it->second);
          if (!bytes) {
            result->Success(flutter::EncodableValue(false));
            return;
          }
          const bool ok = WriteClipboardImage(GetHandle(), *bytes);
          result->Success(flutter::EncodableValue(ok));
          return;
        }
        result->NotImplemented();
      });
  clipboard_channel_ = std::move(clipboard_channel);
  RegisterAudioDevicesChannel();
  RegisterTrayChannel();
  file_drop_channel_ =
      std::make_unique<flutter::MethodChannel<flutter::EncodableValue>>(
          flutter_controller_->engine()->messenger(), kFileDropChannelName,
          &flutter::StandardMethodCodec::GetInstance());
  RegisterPlugins(flutter_controller_->engine());
  SetChildContent(flutter_controller_->view()->GetNativeWindow());
  AttachFileDropTarget(flutter_controller_->view()->GetNativeWindow());

  flutter_controller_->engine()->SetNextFrameCallback([&]() {
    this->Show();
  });

  // Flutter can complete the first frame before the "show window" callback is
  // registered. The following call ensures a frame is pending to ensure the
  // window is shown. It is a no-op if the first frame hasn't completed yet.
  flutter_controller_->ForceRedraw();

  return true;
}

void FlutterWindow::OnDestroy() {
  if (media_playback_active_) {
    SetMediaPlaybackActive(false);
  }
  if (flutter_controller_) {
    RemoveTrayIcon();
    DetachAudioDeviceNotifications();
    DetachFileDropTarget();
    file_drop_channel_ = nullptr;
    audio_devices_channel_ = nullptr;
    tray_channel_ = nullptr;
    clipboard_channel_ = nullptr;
    flutter_controller_ = nullptr;
  }

  Win32Window::OnDestroy();
}

LRESULT
FlutterWindow::MessageHandler(HWND hwnd, UINT const message,
                              WPARAM const wparam,
                              LPARAM const lparam) noexcept {
  if (message == kStopMessageAttentionMessage) {
    attention_stop_posted_ = false;
    if (GetForegroundWindow() == hwnd) {
      StopMessageAttention();
    }
    return 0;
  }

  if (message == WM_ACTIVATE && LOWORD(wparam) != WA_INACTIVE) {
    ScheduleMessageAttentionStop();
  }

  if (message == kAudioDefaultDeviceChangedMessage) {
    std::unique_ptr<AudioDeviceChange> change(
        reinterpret_cast<AudioDeviceChange*>(lparam));
    if (change && audio_devices_channel_) {
      const char* method = change->flow == eCapture
                               ? kDefaultInputDeviceChangedMethod
                               : kDefaultOutputDeviceChangedMethod;
      auto argument = change->device_id.empty()
                          ? std::make_unique<flutter::EncodableValue>()
                          : std::make_unique<flutter::EncodableValue>(
                                change->device_id);
      audio_devices_channel_->InvokeMethod(method, std::move(argument));
    }
    return 0;
  }

  if (message == kTrayMenuCommandMessage) {
    const UINT command = static_cast<UINT>(wparam);
    if (command == kTrayOpenCommand) {
      StopMessageAttention();
      InvokeTrayMethod(kTrayOpenMethod);
    } else if (command == kTrayExitCommand) {
      InvokeTrayMethod(kTrayExitMethod);
    }
    return 0;
  }

  if (message == kTrayCallbackMessage) {
    const UINT mouse_message = static_cast<UINT>(lparam);
    switch (mouse_message) {
      case WM_LBUTTONUP:
      case WM_LBUTTONDBLCLK:
      case NIN_SELECT:
        StopMessageAttention();
        InvokeTrayMethod(kTrayOpenMethod);
        return 0;
      case WM_RBUTTONUP:
      case WM_CONTEXTMENU:
        ShowTrayMenu();
        return 0;
    }
  }

  if (message == WM_TIMER && wparam == kMessageAttentionTimerId) {
    TickMessageAttention();
    return 0;
  }

  // Give Flutter, including plugins, an opportunity to handle window messages.
  if (flutter_controller_) {
    std::optional<LRESULT> result =
        flutter_controller_->HandleTopLevelWindowProc(hwnd, message, wparam,
                                                      lparam);
    if (result) {
      return *result;
    }
  }

  switch (message) {
    case WM_DROPFILES:
      HandleNativeFileDrop(hwnd, reinterpret_cast<HDROP>(wparam));
      return 0;
    case WM_FONTCHANGE:
      flutter_controller_->engine()->ReloadSystemFonts();
      break;
  }

  return Win32Window::MessageHandler(hwnd, message, wparam, lparam);
}
