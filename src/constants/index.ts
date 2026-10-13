export const API_ENDPOINTS = {
  CHAT_COMPLETIONS: '/v1/chat/completions',
  MODELS: '/v1/models',
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
