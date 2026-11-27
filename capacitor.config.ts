import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.cia.app',
  appName: 'CIA',
  webDir: 'dist',
  android: {
    allowMixedContent: false
  }
};

export default config;
