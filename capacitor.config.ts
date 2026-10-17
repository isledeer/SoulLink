import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.isledeer.soullink',
  appName: 'SoulLink',
  webDir: 'dist',
  server: {
    androidScheme: 'https'
  }
};

export default config;
