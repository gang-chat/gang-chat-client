import type { SpeechBridge } from '@gang-chat/core/platform/platform';

/**
 * System voice through the browser's speechSynthesis, which uses SAPI on
 * Windows and AVSpeechSynthesizer on macOS (in Electron and in browsers).
 * PORT: the Flutter desktop client shelled out to PowerShell/SAPI and `say`,
 * the Flutter web build used speechSynthesis; the voices are the same system
 * voices.
 */
export function createBrowserSpeech(): SpeechBridge {
  return {
    speak(text, options) {
      return new Promise<void>((resolve) => {
        if (typeof speechSynthesis === 'undefined') {
          resolve();
          return;
        }
        const utterance = new SpeechSynthesisUtterance(text);
        utterance.volume = Math.max(0, Math.min(1, options.volume));
        utterance.lang = options.language ?? 'zh-CN';
        const voice = speechSynthesis.getVoices().find((v) => v.lang.toLowerCase().startsWith('zh'));
        if (voice) utterance.voice = voice;
        utterance.onend = () => resolve();
        utterance.onerror = () => resolve();
        speechSynthesis.speak(utterance);
      });
    },
    async cancel() {
      if (typeof speechSynthesis !== 'undefined') speechSynthesis.cancel();
    },
  };
}
