; KuDownloader NSIS hooks. The app registers its browser connection (per user,
; HKCU) on first start; uninstall removes it again.

; After installing or updating: tell Windows the icons may have changed, so
; shortcuts and pinned taskbar items don't keep showing a cached old icon.
!macro NSIS_HOOK_POSTINSTALL
  System::Call 'shell32::SHChangeNotify(i 0x08000000, i 0, p 0, p 0)'
!macroend

!macro NSIS_HOOK_PREUNINSTALL
  DeleteRegKey HKCU "Software\Google\Chrome\NativeMessagingHosts\com.kuduy.kudownloader"
  DeleteRegKey HKCU "Software\Chromium\NativeMessagingHosts\com.kuduy.kudownloader"
  DeleteRegKey HKCU "Software\Microsoft\Edge\NativeMessagingHosts\com.kuduy.kudownloader"
  DeleteRegKey HKCU "Software\BraveSoftware\Brave-Browser\NativeMessagingHosts\com.kuduy.kudownloader"
  DeleteRegKey HKCU "Software\Vivaldi\NativeMessagingHosts\com.kuduy.kudownloader"
  DeleteRegKey HKCU "Software\Mozilla\NativeMessagingHosts\com.kuduy.kudownloader"
!macroend
