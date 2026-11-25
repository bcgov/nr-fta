import { Loading } from '@carbon/react';

import type { FC } from 'react';

/**
 * A button's icon while its action runs: Carbon's small spinner, passed as `renderIcon` in place
 * of the button's usual icon. Decorative; the button's label says what is happening.
 */
const BusyIcon: FC = () => <Loading small withOverlay={false} description="" />;

export default BusyIcon;
