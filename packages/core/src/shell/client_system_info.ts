import type { HostOs } from '../platform/platform';
import { hasPlatform, platform } from '../platform/platform';

export function clientSystemName(os: HostOs): string {
  switch (os) {
    case 'android':
      return 'Android';
    case 'ios':
      return 'iOS';
    case 'linux':
      return 'Linux';
    case 'macos':
      return 'macOS';
    case 'windows':
      return 'Windows';
    case 'web':
      return 'Web';
  }
}

export function gangChatClientUserAgent(os: HostOs): string {
  return `GangChat Client (${clientSystemName(os)})`;
}

export function currentGangChatClientUserAgent(): string {
  return gangChatClientUserAgent(hasPlatform() ? platform().os : 'web');
}
