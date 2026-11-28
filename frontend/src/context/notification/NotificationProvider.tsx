import { ToastNotification } from '@carbon/react';
import { useState, useEffect, type ReactNode, useCallback, useMemo, useRef } from 'react';

import { safeErrorMessage } from '@/lib/errorMessage';

import { NotificationContext, type NotificationContent } from './NotificationContext';

/**
 * Mirrors nr-rept's NotificationProvider verbatim. The toast slides
 * in from the top-right of the viewport (styled in src/styles/index.scss),
 * stays for `timeout` ms, then slides out. Callers fire it via
 * useNotification().display({ kind, title, subtitle, timeout }).
 */
// A toast raised this recently survives a page change: it reports the action that navigated
// (e.g. "Saved", then off to the saved record).
const NAVIGATION_GRACE_MS = 1500;

export const NotificationProvider = ({ children }: { children: ReactNode }) => {
  const [notificationContent, setNotificationContent] = useState<NotificationContent | null>(null);
  const [notificationClass, setNotificationClass] = useState<string>('slide-in');
  const shownAt = useRef(0);

  const display = useCallback((content: NotificationContent) => {
    // Error / warning toasts stay until the user closes them. The
    // caller's timeout is ignored for those kinds so an information
    // error doesn't disappear before the user has read it. Success
    // and info toasts still auto-dismiss after their requested
    // timeout (typical 5-6 s), as does a problem toast marked autoDismiss.
    const isProblem =
      content.kind === 'error' || content.kind === 'warning' || content.kind === 'warning-alt';
    // Never let a raw backend/DB error (ORA-…, SQL, stack trace, error
    // JSON) reach a toast — swap any technical subtitle for a friendly
    // line. Clean messages (validation, etc.) pass through untouched.
    const safe =
      isProblem && content.subtitle !== undefined
        ? { ...content, subtitle: safeErrorMessage(content.subtitle) }
        : content;
    shownAt.current = Date.now();
    setNotificationClass('slide-in');
    setNotificationContent(isProblem && !content.autoDismiss ? { ...safe, timeout: 0 } : safe);
  }, []);

  const clear = useCallback(() => setNotificationContent(null), []);
  const clearStale = useCallback(() => {
    if (Date.now() - shownAt.current > NAVIGATION_GRACE_MS) setNotificationContent(null);
  }, []);
  const value = useMemo(() => ({ display, clear, clearStale }), [display, clear, clearStale]);

  const onClose = useCallback(() => {
    setNotificationClass('slide-out');
    notificationContent?.onClose?.();
    setNotificationContent(null);
  }, [notificationContent]);

  useEffect(() => {
    if (notificationContent && notificationContent.timeout > 0) {
      if (notificationClass === 'slide-in') {
        const timer = setTimeout(() => {
          setNotificationClass('slide-out');
        }, notificationContent.timeout - 300);
        return () => clearTimeout(timer);
      }
    }
  }, [notificationClass, notificationContent]);

  // Only a long message (e.g. a refused Prepare's one-refusal-per-row list) gets a scroll box:
  // on short text a scroll box can overflow by a pixel of line height and show a stray scrollbar.
  const subtitle = notificationContent?.subtitle;
  const long =
    typeof subtitle === 'string' && (subtitle.split('\n').length > 8 || subtitle.length > 600);

  return (
    <NotificationContext.Provider value={value}>
      {children}
      {notificationContent && (
        <ToastNotification
          className={long ? `${notificationClass} toast--long` : notificationClass}
          lowContrast
          aria-label="closes notification"
          caption={notificationContent.caption}
          kind={notificationContent.kind}
          onClose={onClose}
          onCloseButtonClick={notificationContent.onCloseButtonClick}
          role="status"
          statusIconDescription="notification"
          subtitle={notificationContent.subtitle}
          timeout={notificationContent.timeout}
          title={notificationContent.title}
        >
          {notificationContent.children}
        </ToastNotification>
      )}
    </NotificationContext.Provider>
  );
};

export default NotificationProvider;
