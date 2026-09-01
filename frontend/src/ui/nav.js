import {
  Building2,
  CircleUserRound,
  LayoutGrid,
  ShieldCheck,
  Users,
  Waves,
} from 'lucide-react';

/**
 * One list, used by the dock and by anything else that needs to name a
 * destination. Keeping the icon next to the route means the two cannot drift.
 */
export const DESTINATIONS = [
  { to: '/', end: true, label: 'Overview', icon: LayoutGrid },
  { to: '/customers', label: 'Customers', icon: Users },
  { to: '/banks', label: 'Member banks', icon: Building2 },
  { to: '/privacy', label: 'Privacy layer', icon: ShieldCheck },
  { to: '/risk', label: 'Risk simulator', icon: Waves },
  { to: '/operators', label: 'Operators', icon: CircleUserRound, superAdminOnly: true },
];
