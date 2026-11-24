import { createContext, type ReactNode } from 'react';

export type NotificationContent = {
  caption?: string;
  kind: 'error' | 'info' | 'info-square' | 'success' | 'warning' | 'warning-alt' | undefined;
  onClose?: () => void;
  onCloseButtonClick?: () => void;
  subtitle?: string;
  timeout: number;
  /**
   * Lets an error or warning toast still go after `timeout`. Without it those kinds stay until
   * closed; set it only for a short prompt, not a failure the user needs to read.
   */
  autoDismiss?: boolean;
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
