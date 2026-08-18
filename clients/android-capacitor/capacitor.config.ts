import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.deeptutor.mobile',
  appName: 'DeepTutor',
  // Local launcher page (src/index.html): lets the user pick which DeepTutor
  // server to open. No server.url is set, so Capacitor serves these assets
  // from its local scheme and we navigate to the chosen server at runtime.
  webDir: 'src',
  android: {
    allowMixedContent: true,
  },
};

export default config;
