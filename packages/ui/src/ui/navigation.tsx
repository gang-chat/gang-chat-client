import type { IconData } from './icon';
import { Segment, SegmentedControl } from './segmented_control';

export class NavigationItem<T> {
  constructor(opts: { value: T; label: string; icon?: IconData | null }) {
    this.value = opts.value;
    this.label = opts.label;
    this.icon = opts.icon ?? null;
  }

  readonly value: T;
  readonly label: string;
  readonly icon: IconData | null;
}

export interface NavigationTabsProps<T> {
  items: readonly NavigationItem<T>[];
  value: T;
  onChanged: (value: T) => void;
  expanded?: boolean;
}

export function NavigationTabs<T>({ items, value, onChanged, expanded = false }: NavigationTabsProps<T>) {
  return (
    <SegmentedControl<T>
      value={value}
      onChanged={onChanged}
      expanded={expanded}
      segments={items.map((item) => new Segment<T>({ value: item.value, label: item.label, icon: item.icon }))}
    />
  );
}
