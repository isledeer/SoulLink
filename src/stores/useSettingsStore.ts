import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { ReasoningEffort } from '../types/api';

interface SettingsState {
  apiBaseUrl: string;
  apiKey: string;
  modelName: string;
  compatibilityMode: boolean;
  /** 思考强度；undefined = 关闭（不传任何思考相关参数） */
  reasoningEffort: ReasoningEffort | undefined;
  setApiBaseUrl: (url: string) => void;
  setApiKey: (key: string) => void;
  setModelName: (name: string) => void;
  setCompatibilityMode: (mode: boolean) => void;
  /** 命名前缀遵循项目约定：store 内 setter 统一带 setStore，避免与页面本地 setter 撞名 */
  setStoreReasoningEffort: (v: ReasoningEffort | undefined) => void;
  testConnection: () => Promise<boolean>;
}

export const useSettingsStore = create<SettingsState>()(
  persist(
    (set, get) => ({
      apiBaseUrl: '',
      apiKey: '',
      modelName: 'gpt-3.5-turbo',
      compatibilityMode: false,
      reasoningEffort: undefined,

      setApiBaseUrl: (url) => set({ apiBaseUrl: url }),
      setApiKey: (key) => set({ apiKey: key }),
      setModelName: (name) => set({ modelName: name }),
      setCompatibilityMode: (mode) => set({ compatibilityMode: mode }),
      setStoreReasoningEffort: (v) => set({ reasoningEffort: v }),

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
        reasoningEffort: s.reasoningEffort,
      }),
    }
  )
);
