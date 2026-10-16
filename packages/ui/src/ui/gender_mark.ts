import { argb } from './tokens';

export class GenderMarkData {
  constructor(opts: { symbol: string; color: string }) {
    this.symbol = opts.symbol;
    this.color = opts.color;
  }

  readonly symbol: string;
  readonly color: string;
}

const kMale = new GenderMarkData({ symbol: '♂', color: argb(0xff5aa7ff) });
const kFemale = new GenderMarkData({ symbol: '♀', color: argb(0xffff6f8f) });

export function genderMark(value: string | null | undefined): GenderMarkData | null {
  switch (_nonEmpty(value)?.toLowerCase()) {
    case 'male':
    case 'm':
    case 'man':
      return kMale;
    case 'female':
    case 'f':
    case 'woman':
      return kFemale;
    default:
      return null;
  }
}

function _nonEmpty(value: string | null | undefined): string | null {
  const trimmed = value?.trim();
  if (trimmed == null || trimmed.length === 0) return null;
  return trimmed;
}
