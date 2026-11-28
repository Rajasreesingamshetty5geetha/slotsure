import Svg, { Circle, Defs, Ellipse, LinearGradient, Path, Rect, Stop, Text as SvgText } from 'react-native-svg';

export function Van({ width = 220 }: { width?: number }) {
  return (
    <Svg width={width} height={(width * 140) / 220} viewBox="0 0 220 140">
      <Defs>
        <LinearGradient id="vb" x1="0" y1="0" x2="1" y2="1">
          <Stop offset="0" stopColor="#FF8A3D" />
          <Stop offset="1" stopColor="#E8501A" />
        </LinearGradient>
        <LinearGradient id="bx" x1="0" y1="0" x2="0" y2="1">
          <Stop offset="0" stopColor="#F6C27A" />
          <Stop offset="1" stopColor="#D99A4E" />
        </LinearGradient>
      </Defs>
      <Ellipse cx="110" cy="128" rx="92" ry="8" fill="rgba(20,20,20,0.12)" />
      <Rect x="44" y="6" width="70" height="44" rx="5" fill="url(#bx)" />
      <Path d="M79 6v44" stroke="#C98A3E" strokeWidth="3" />
      <Rect x="66" y="20" width="26" height="10" rx="2" fill="#FBE3BC" />
      <Path d="M20 52h130l30 26 18 6v28a6 6 0 01-6 6H20a6 6 0 01-6-6V58a6 6 0 016-6z" fill="url(#vb)" />
      <Path d="M150 56h14l22 22h-36z" fill="#2B2B2B" />
      <Rect x="26" y="62" width="110" height="34" rx="6" fill="rgba(255,255,255,0.18)" />
      <SvgText x="81" y="84" textAnchor="middle" fontSize="13" fontWeight="800" fill="#FFFFFF">
        SlotSure
      </SvgText>
      <Rect x="190" y="92" width="10" height="6" rx="2" fill="#FFE08A" />
      <Circle cx="56" cy="116" r="15" fill="#1F1F1F" />
      <Circle cx="56" cy="116" r="6" fill="#BDBDBD" />
      <Circle cx="164" cy="116" r="15" fill="#1F1F1F" />
      <Circle cx="164" cy="116" r="6" fill="#BDBDBD" />
    </Svg>
  );
}

export function Parcel({ size = 56 }: { size?: number }) {
  return (
    <Svg width={size} height={size} viewBox="0 0 64 64">
      <Path d="M32 6l24 12v28L32 58 8 46V18z" fill="#EBB46B" />
      <Path d="M8 18l24 12 24-12" fill="none" stroke="#C98A3E" strokeWidth="2.5" />
      <Path d="M32 30v28" stroke="#C98A3E" strokeWidth="2.5" />
      <Path d="M20 12l24 12v8" fill="none" stroke="#FBE3BC" strokeWidth="4" />
    </Svg>
  );
}

export function Pin({ size = 46, color = '#E8501A' }: { size?: number; color?: string }) {
  return (
    <Svg width={size} height={(size * 56) / 44} viewBox="0 0 44 56">
      <Path d="M22 2C11 2 3 10 3 21c0 13 19 33 19 33s19-20 19-33C41 10 33 2 22 2z" fill={color} />
      <Circle cx="22" cy="21" r="8" fill="#FFFFFF" />
    </Svg>
  );
}

const icons = {
  back: 'M19 12H5M11 6l-6 6 6 6',
  arrow: 'M5 12h14M13 6l6 6-6 6',
  search: 'M11 4a7 7 0 100 14 7 7 0 000-14zM20 20l-4-4',
  pin: 'M12 21s7-6.5 7-12a7 7 0 00-14 0c0 5.5 7 12 7 12zM12 6.5a2.5 2.5 0 100 5 2.5 2.5 0 000-5z',
  locate: 'M12 8a4 4 0 100 8 4 4 0 000-8zM12 2v3M12 19v3M2 12h3M19 12h3',
  home: 'M4 11l8-7 8 7v9h-5v-6H9v6H4z',
  calendar: 'M6 5h12a2 2 0 012 2v11a2 2 0 01-2 2H6a2 2 0 01-2-2V7a2 2 0 012-2zM4 10h16M9 3v4M15 3v4',
  user: 'M12 4a4 4 0 100 8 4 4 0 000-8zM4 21c0-4 4-6 8-6s8 2 8 6',
  clock: 'M12 3.5a8.5 8.5 0 100 17 8.5 8.5 0 000-17zM12 7v5l3 2',
  card: 'M5 6h14a2 2 0 012 2v9a2 2 0 01-2 2H5a2 2 0 01-2-2V8a2 2 0 012-2zM3 10h18M7 15h3',
  shield: 'M12 3l7 3v6c0 4-3 7-7 9-4-2-7-5-7-9V6zM9 12l2 2 4-4',
  check: 'M5 12l4 4 10-10',
  bell: 'M6 16V11a6 6 0 0112 0v5l1.5 2h-15zM10 20a2 2 0 004 0',
  headphones: 'M4 15v-3a8 8 0 0116 0v3M5 14h1a1 1 0 011 1v4a1 1 0 01-1 1H5a2 2 0 01-2-2v-2a2 2 0 012-2zM18 14h1a2 2 0 012 2v2a2 2 0 01-2 2h-1a1 1 0 01-1-1v-4a1 1 0 011-1z',
  truck: 'M3 7h11v9H3zM14 10h4l3 3v3h-7M7 17.5a1.8 1.8 0 100 .1M17 17.5a1.8 1.8 0 100 .1',
  logout: 'M15 4h3a2 2 0 012 2v12a2 2 0 01-2 2h-3M10 16l-4-4 4-4M6 12h10',
  refresh: 'M20 11a8 8 0 10-2.3 5.7M20 5v6h-6',
  alert: 'M12 3l9.5 17h-19zM12 10v4M12 17v.5',
  close: 'M6 6l12 12M18 6L6 18',
  bag: 'M6 8h12l-1 12H7zM9 8V6a3 3 0 016 0v2',
  phone: 'M5 4h4l2 5-2.5 1.5a11 11 0 005 5L15 13l5 2v4a2 2 0 01-2 2A16 16 0 013 6a2 2 0 012-2z',
  cash: 'M3 7h18v10H3zM12 9.5a2.5 2.5 0 100 5 2.5 2.5 0 000-5zM6 10v4M18 10v4',
  chart: 'M4 20V10M10 20V4M16 20v-7M22 20H2',
};

export type IconName = keyof typeof icons;

export function Icon({ name, size = 20, color = '#141414', stroke = 2 }: { name: IconName; size?: number; color?: string; stroke?: number }) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24" fill="none">
      <Path d={icons[name]} stroke={color} strokeWidth={stroke} strokeLinecap="round" strokeLinejoin="round" />
    </Svg>
  );
}
