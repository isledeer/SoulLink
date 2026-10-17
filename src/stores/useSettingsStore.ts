import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { ReasoningEffort } from '../types/api';
import { API_ENDPOINTS } from '../constants';
import { normalizeBaseUrl } from '../services/apiClient';

interface SettingsState {
  apiBaseUrl: string;
  apiKey: string;
  modelName: string;
  compatibilityMode: boolean;
  /** 思考强度；undefined = 关闭（不传任何思考相关参数） */
  reasoningEffort: ReasoningEffort | undefined;
  /** 从 /v1/models 拉取到的模型 id 列表；仅内存态，不落盘 */
  availableModels: string[];
  setApiBaseUrl: (url: string) => void;
  setApiKey: (key: string) => void;
  setModelName: (name: string) => void;
  setCompatibilityMode: (mode: boolean) => void;
  /** 命名前缀遵循项目约定：store 内 setter 统一带 setStore，避免与页面本地 setter 撞名 */
  setStoreReasoningEffort: (v: ReasoningEffort | undefined) => void;
  setStoreAvailableModels: (models: string[]) => void;
  /** 拉取模型列表；失败时 **抛出** 给 UI 提示，不静默吞掉 */
  fetchModels: () => Promise<void>;
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
      availableModels: [],

      setApiBaseUrl: (url) => set({ apiBaseUrl: url }),
      setApiKey: (key) => set({ apiKey: key }),
      setModelName: (name) => set({ modelName: name }),
      setCompatibilityMode: (mode) => set({ compatibilityMode: mode }),
      setStoreReasoningEffort: (v) => set({ reasoningEffort: v }),
      setStoreAvailableModels: (models) => set({ availableModels: models }),

      fetchModels: async () => {
        const { apiBaseUrl, apiKey, setStoreAvailableModels } = get();
        if (!apiBaseUrl) return;

        try {
          const url = normalizeBaseUrl(apiBaseUrl) + '/v1' + API_ENDPOINTS.MODELS;
          const res = await fetch(url, {
            headers: { Authorization: `Bearer ${apiKey}` },
          });
          if (!res.ok) throw new Error('Fetch failed');

          // OpenAI 兼容格式：{ data: [{ id: 'gpt-4o', ... }, ...] }
          const data: unknown = await res.json();
          const list = (data as { data?: { id?: unknown }[] } | null)?.data;
          const models = Array.isArray(list)
            ? list
                .map((m) => m?.id)
                .filter((id): id is string => typeof id === 'string' && id.length > 0)
            : [];

          setStoreAvailableModels(models);
        } catch (err) {
          console.error(err);
          throw err; // 抛出给 UI 处理，禁止静默失败
        }
      },

      testConnection: async () => {
        const { apiBaseUrl, apiKey } = get();

        if (!apiBaseUrl || !apiKey) return false;

        try {
          // 与 fetchModels 同一条拼 URL 规则：归一化 baseUrl 后统一补 /v1
          const url = normalizeBaseUrl(apiBaseUrl) + '/v1' + API_ENDPOINTS.MODELS;
          const response = await fetch(url, {
            headers: { Authorization: `Bearer ${apiKey}` },
          });
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
