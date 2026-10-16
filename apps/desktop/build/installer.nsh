; Writes the install timestamp the About page reads
; (gang_chat_install_info.txt next to GangChat.exe), like the Flutter installer.
!macro customInstall
  ${GetTime} "" "L" $0 $1 $2 $3 $4 $5 $6
  FileOpen $7 "$INSTDIR\gang_chat_install_info.txt" w
  FileWrite $7 "$2-$1-$0T$4:$5:$6"
  FileClose $7
!macroend
