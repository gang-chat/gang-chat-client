/**
 * Keeps desktop scrollbars in a dedicated trailing gutter instead of
 * painting their thumbs over list cards and action buttons.
 *
 * PORT: Flutter painted scrollbars over the content, so the behavior padded
 * vertical scroll views by [verticalScrollbarGutter]. The renderer uses
 * classic (non-overlay) Chromium scrollbars styled in the app's global CSS,
 * which already reserve their own gutter, so there is nothing to add at the
 * scroll-view level. The class and constant stay for code that reads them.
 */
export class GangScrollBehavior {
  static readonly verticalScrollbarGutter = 8;
}
