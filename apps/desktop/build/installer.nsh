; Writes the install timestamp the About page reads
; (gang_chat_install_info.txt next to GangChat.exe), like the Flutter installer.
!macro customInstall
  ${GetTime} "" "L" $0 $1 $2 $3 $4 $5 $6
  FileOpen $7 "$INSTDIR\gang_chat_install_info.txt" w
  FileWrite $7 "$2-$1-$0T$4:$5:$6"
  FileClose $7
!macroend

; Moving from the Flutter desktop app (installer/windows/gang_chat.nsi). It
; registered "Uninstall\Gang Chat" and installed into the same folder, so its
; own uninstaller runs silently first: the new app doesn't land on top of the
; old files, and "Installed apps" doesn't list Gang Chat twice. It removes only
; the program files, shortcuts and that key; settings and sign-in under
; AppData stay. The Flutter installer was 32-bit NSIS, so the key sits in the
; 32-bit registry view; electron-builder has switched to the 64-bit one.
!define FLUTTER_UNINSTALL_KEY "Software\Microsoft\Windows\CurrentVersion\Uninstall\Gang Chat"

!macro customInit
  SetRegView 32
  ReadRegStr $R8 HKLM "${FLUTTER_UNINSTALL_KEY}" "InstallLocation"
  ${if} $R8 == ""
    SetRegView 64
    ReadRegStr $R8 HKLM "${FLUTTER_UNINSTALL_KEY}" "InstallLocation"
  ${endif}
  ${if} $R8 != ""
  ${andIf} ${FileExists} "$R8\Uninstall.exe"
    ; The Flutter app may still be closing after it started this installer.
    nsExec::Exec 'taskkill /IM GangChat.exe'
    Pop $R9
    Sleep 1500
    nsExec::Exec 'taskkill /F /IM GangChat.exe'
    Pop $R9
    ExecWait '"$R8\Uninstall.exe" /S _?=$R8'
    Delete "$R8\Uninstall.exe"
    RMDir "$R8"
    DeleteRegKey HKLM "${FLUTTER_UNINSTALL_KEY}"
  ${endif}
  SetRegView 64
!macroend
