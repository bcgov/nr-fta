import { useEffect, useRef } from 'react';
import { useLocation } from 'react-router-dom';

import { useNotification } from '@/context/notification/useNotification';

/**
 * Takes down the toast when the user moves to another page, so an error from one screen doesn't
 * follow them onto the next. A toast the navigating action itself raised is kept (see
 * clearStale). Renders nothing; mount it once inside the router.
 */
const NotificationRouteReset = () => {
  const { pathname } = useLocation();
  const { clearStale } = useNotification();
  const previous = useRef(pathname);

  useEffect(() => {
    if (previous.current === pathname) return;
    previous.current = pathname;
    clearStale();
  }, [pathname, clearStale]);

  return null;
};

export default NotificationRouteReset;
