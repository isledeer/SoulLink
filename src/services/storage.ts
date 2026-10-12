import { STORAGE_KEYS } from '../constants';

const ENCRYPTION_PREFIX = 'enc_';

/**
 * 简单混淆（**不是加密**，仅避免 apiKey 明文直接暴露在 localStorage 里）。
 * encodeURIComponent 先转成纯 ASCII，保证中文等多字节字符也能被 btoa 处理。
 */
function simpleEncrypt(value: string): string {
  return ENCRYPTION_PREFIX + btoa(encodeURIComponent(value));
}

function simpleDecrypt(value: string): string {
  if (!value.startsWith(ENCRYPTION_PREFIX)) return value;
  try {
    return decodeURIComponent(atob(value.slice(ENCRYPTION_PREFIX.length)));
  } catch {
    return value;
  }
}

interface StorageItem<T> {
  value: T;
  expiry?: number;
}

export const storage = {
  get<T>(key: string): T | null {
    try {
      const raw = localStorage.getItem(key);
      if (!raw) return null;

      const item: StorageItem<T> = JSON.parse(raw);

      if (item.expiry && Date.now() > item.expiry) {
        localStorage.removeItem(key);
        return null;
      }

      return item.value;
    } catch {
      return null;
    }
  },

  set<T>(key: string, value: T, ttlMs?: number): void {
    const item: StorageItem<T> = { value };
    if (ttlMs) {
      item.expiry = Date.now() + ttlMs;
    }
    localStorage.setItem(key, JSON.stringify(item));
  },

  remove(key: string): void {
    localStorage.removeItem(key);
  },

  getAPIConfig(): Record<string, unknown> | null {
    const config = storage.get<Record<string, unknown>>(STORAGE_KEYS.API_CONFIG);
    if (!config || typeof config !== 'object') return null;

    if (typeof config.apiKey === 'string') {
      config.apiKey = simpleDecrypt(config.apiKey);
    }
    return config;
  },

  setAPIConfig(config: Record<string, unknown>): void {
    const safeConfig = { ...config };
    if (typeof safeConfig.apiKey === 'string') {
      safeConfig.apiKey = simpleEncrypt(safeConfig.apiKey);
    }
    storage.set(STORAGE_KEYS.API_CONFIG, safeConfig);
  },
};
