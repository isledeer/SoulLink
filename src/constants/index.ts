import type { ReasoningProvider } from '../types/api';

/**
 * 端点常量只存**纯路径**（不带 /v1）。
 * /v1 前缀统一由 normalizeBaseUrl(baseUrl) + '/v1' 拼接，
 * 这样用户填 `https://api.openai.com` 或 `https://api.openai.com/v1` 都不会出现 /v1/v1。
 */
export const API_ENDPOINTS = {
  CHAT_COMPLETIONS: '/chat/completions',
  MODELS: '/models',
} as const;

export const STORAGE_KEYS = {
  API_CONFIG: 'ai_rp_api_config',
  ACTIVE_SAVE_ID: 'ai_rp_active_save_id',
  THEME: 'ai_rp_theme',
  SETTINGS: 'ai_rp_settings',
} as const;

export const GAME_CONFIG = {
  DEFAULT_AFFINITY: 0,
  MAX_AFFINITY: 100,
  MIN_AFFINITY: -100,
  MAX_INVENTORY_SLOTS: 50,
  MEMORY_COMPRESSION_THRESHOLD: 40,
  MAX_CONTEXT_TOKENS: 4096,
  STREAM_CHUNK_SIZE: 1024,
} as const;

/**
 * model 名片段 → 厂商族。键统一小写，匹配时对 modelName 做 toLowerCase 后 includes。
 * 顺序即优先级：越靠前的 key 越先被命中（'gpt-' 在 'openai' 之后，两者不冲突）。
 */
export const REASONING_PROVIDERS: Record<string, ReasoningProvider> = {
  openai: 'openai',
  'gpt-': 'openai',
  'o1': 'openai',
  'o3': 'openai',
  deepseek: 'deepseek',
  claude: 'anthropic',
  gemini: 'gemini',
};
