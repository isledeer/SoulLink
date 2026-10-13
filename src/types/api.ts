export interface APIConfig {
  baseUrl: string;
  apiKey: string;
  model: string;
  maxTokens: number;
  temperature: number;
  compatibleMode: boolean;
  /** 思考强度；undefined = 关闭，不向请求体塞任何思考参数 */
  reasoningEffort?: ReasoningEffort;
}

/** 思考强度档位；undefined 表示关闭 */
export type ReasoningEffort = 'low' | 'medium' | 'high';

/** 由 model 名推断出的厂商族，决定思考强度用哪套请求参数 */
export type ReasoningProvider =
  | 'openai'
  | 'deepseek'
  | 'anthropic'
  | 'gemini'
  | 'unknown';
