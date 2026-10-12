import { create } from 'zustand';
import { persist } from 'zustand/middleware';

interface SettingsState {
  apiBaseUrl: string;
  apiKey: string;
  modelName: string;
  compatibilityMode: boolean;
  setApiBaseUrl: (url: string) => void;
  setApiKey: (key: string) => void;
  setModelName: (name: string) => void;
  setCompatibilityMode: (mode: boolean) => void;
  testConnection: () => Promise<boolean>;
}

export const useSettingsStore = create<SettingsState>()(
  persist(
    (set, get) => ({
      apiBaseUrl: '',
      apiKey: '',
      modelName: 'gpt-3.5-turbo',
      compatibilityMode: false,

      setApiBaseUrl: (url) => set({ apiBaseUrl: url }),
      setApiKey: (key) => set({ apiKey: key }),
      setModelName: (name) => set({ modelName: name }),
      setCompatibilityMode: (mode) => set({ compatibilityMode: mode }),

      testConnection: async () => {
        const { apiBaseUrl, apiKey } = get();

        if (!apiBaseUrl || !apiKey) return false;

        try {
          const response = await fetch(
            `${apiBaseUrl.replace(/\/+$/, '')}/models`,
            {
              headers: { Authorization: `Bearer ${apiKey}` },
            }
          );
          return response.ok;
        } catch {
          return false;
        }
      },
    }),
    {
      name: 'ai-roleplay-settings',
      partialize: (s) => ({
        apiBaseUrl: s.apiBaseUrl,
        apiKey: s.apiKey,
        modelName: s.modelName,
        compatibilityMode: s.compatibilityMode,
      }),
    }
  )
);
