import { createContext, type ReactNode } from 'react';

export type NotificationContent = {
  caption?: string;
  kind: 'error' | 'info' | 'info-square' | 'success' | 'warning' | 'warning-alt' | undefined;
  onClose?: () => void;
  onCloseButtonClick?: () => void;
  subtitle?: string;
  timeout: number;
  title: string;
  children?: ReactNode;
};

export type NotificationContextData = {
  display: (content: NotificationContent) => void;
  /** Takes down the toast on screen, if any (a new search, or the user leaving the page). */
  clear: () => void;
  /** As clear, but keeps a toast shown in the last moment: the one an action raised just before it navigated. */
  clearStale: () => void;
};

export const NotificationContext = createContext<NotificationContextData | undefined>(undefined);
