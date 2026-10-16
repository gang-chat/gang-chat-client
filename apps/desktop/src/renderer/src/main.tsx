import './global.css';

import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { setPlatform } from '@gang-chat/core/platform/platform';

import { desktopBridge } from './platform/desktop_bridge';
import { createDesktopPlatform } from './platform/desktop_platform';
import { DesktopRoot } from './desktop_root';

// The platform bridge must be installed before any shared module touches
// storage, HTTP or the window.
const bridge = desktopBridge();
setPlatform(createDesktopPlatform(bridge));

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <DesktopRoot />
  </StrictMode>,
);
