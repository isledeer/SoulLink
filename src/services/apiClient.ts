import { AppError, APIError, NetworkError } from './errorHandler';
import { API_ENDPOINTS, REASONING_PROVIDERS } from '../constants';
import type { APIConfig, ReasoningEffort, ReasoningProvider } from '../types/api';
import type { Message } from '../types/message';

/**
 * 归一化 baseUrl：去掉首尾空白与**尾部所有斜杠**，并把结尾的 `/v1` 截掉。
 *
 * 之后调用方统一拼 `normalizeBaseUrl(baseUrl) + '/v1' + API_ENDPOINTS.XXX`，
 * 于是 `https://api.openai.com`、`https://api.openai.com/`、
 * `https://api.openai.com/v1`、`https://api.openai.com/v1/` 四种写法
 * 都会得到同一个 `https://api.openai.com/v1/chat/completions`，彻底消灭 /v1/v1。
 */
export function normalizeBaseUrl(url: string): string {
  let clean = url.trim().replace(/\/+$/, ''); // 去掉尾部所有斜杠
  if (clean.endsWith('/v1')) {
    clean = clean.slice(0, -3); // 截掉 /v1
  }
  return clean;
}

const DEFAULT_MAX_RETRIES = 3;
const BASE_RETRY_DELAY_MS = 1000;
const MAX_RETRY_DELAY_MS = 10000;

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/** 指数退避：1000 * 2^attempt，上限 10000ms */
function backoffDelay(attempt: number): number {
  return Math.min(BASE_RETRY_DELAY_MS * Math.pow(2, attempt), MAX_RETRY_DELAY_MS);
}

/** 用户主动取消导致的异常不能被重试吞掉，必须原样冒泡 */
function isAbortError(error: unknown): boolean {
  return (
    (error instanceof DOMException && error.name === 'AbortError') ||
    (error instanceof Error && error.name === 'AbortError')
  );
}

async function fetchWithRetry(
  url: string,
  options: RequestInit,
  maxRetries = DEFAULT_MAX_RETRIES
): Promise<Response> {
  let lastError: Error | null = null;
  let lastResponse: Response | null = null;

  for (let attempt = 0; attempt < maxRetries; attempt++) {
    try {
      const response = await fetch(url, options);

      if (!response.ok) {
        lastResponse = response;
      }

      // 限流与服务端错误才重试，4xx（除 429）直接返回交给调用方判断
      if (response.status === 429 || response.status >= 500) {
        await sleep(backoffDelay(attempt));
        continue;
      }

      return response;
    } catch (error) {
      if (isAbortError(error)) throw error;

      lastError = error instanceof Error ? error : new Error(String(error));

      if (attempt < maxRetries - 1) {
        await sleep(backoffDelay(attempt));
      }
    }
  }

  // 收口：重试耗尽后按「已知错误 > 最后一次失败响应 > 网络异常」的优先级抛出
  if (lastError instanceof AppError) throw lastError;
  if (lastResponse) {
    throw new APIError(
      lastResponse.status,
      `API ${lastResponse.status} after retries`
    );
  }
  throw new NetworkError();
}

/** 流式增量的类型：正文 or 思维链。UI 据此决定往哪个字段追加 */
export interface StreamChunk {
  type: 'content' | 'reasoning';
  text: string;
}

/**
 * 解析单个 SSE event，返回其中按出现顺序拼接出的增量列表。
 *
 * 同时识别两种 delta：
 * - `delta.content`           → 正文
 * - `(delta.reasoning_content || delta.reasoning || delta.thinking)` → 思维链（DeepSeek-R1 / 部分兼容端点）
 *
 * 返回数组而不是字符串：一个 event 里可能既有思维链又有正文，
 * 合成一个字符串会丢掉「哪段是思维链」的信息。解析失败返回空数组。
 */
function parseSSEEvent(event: string): StreamChunk[] {
  const chunks: StreamChunk[] = [];

  for (const line of event.split('\n')) {
    const trimmed = line.trim();
    if (!trimmed.startsWith('data:')) continue;

    const data = trimmed.slice('data:'.length).trim();
    if (!data || data === '[DONE]') continue;

    try {
      const parsed = JSON.parse(data) as {
        choices?: {
          delta?: { content?: string; reasoning_content?: string; reasoning?: string; thinking?: string };
        }[];
      };
      const delta = parsed?.choices?.[0]?.delta;
      if (!delta) continue;

      // 顺序固定为先思维链后正文：同一 delta 里两者同时到达时，
      // 思维链在语义上总是先于正文，与 R1 的实际输出顺序一致。
      if ((delta.reasoning_content || delta.reasoning || delta.thinking)) {
        chunks.push({ type: 'reasoning', text: (delta.reasoning_content || delta.reasoning || delta.thinking || '') });
      }
      if (delta.content) {
        chunks.push({ type: 'content', text: delta.content });
      }
    } catch {
      // 单个 event 解析失败静默跳过，不影响后续 event
    }
  }

  return chunks;
}

/**
 * 流式读取 SSE 响应体，逐块回调。
 *
 * 返回**正文**的完整文本（不含思维链）：调用方拿它做 <game> 解析与空白判定。
 * 思维链不在这里聚合，由 onChunk 逐块交给 store 累积到消息的 reasoning 字段。
 */
async function readSSEStream(
  body: ReadableStream<Uint8Array>,
  onChunk: (chunk: StreamChunk) => void
): Promise<string> {
  const reader = body.getReader();
  const decoder = new TextDecoder();

  let fullContent = '';
  let buffer = '';

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;

    buffer += decoder.decode(value, { stream: true });

    // SSE 以空行分隔 event，不能用 '\n'，否则多行 event 会被切碎
    const events = buffer.split('\n\n');
    buffer = events.pop() ?? '';

    for (const event of events) {
      for (const chunk of parseSSEEvent(event)) {
        if (chunk.type === 'content') fullContent += chunk.text;
        onChunk(chunk);
      }
    }
  }

  // 冲刷解码器与残留 buffer：部分服务端最后一个 event 不带结尾空行
  buffer += decoder.decode();
  for (const chunk of parseSSEEvent(buffer)) {
    if (chunk.type === 'content') fullContent += chunk.text;
    onChunk(chunk);
  }

  return fullContent;
}

/**
 * 由 model 名推断厂商族：对 modelName 转小写后遍历 REASONING_PROVIDERS 的 key 做 includes。
 * 命中即返回对应族，全部不中返回 'unknown'。
 */
export function detectReasoningProvider(modelName: string): ReasoningProvider {
  const name = (modelName || '').toLowerCase();

  for (const key of Object.keys(REASONING_PROVIDERS)) {
    if (name.includes(key)) return REASONING_PROVIDERS[key];
  }

  return 'unknown';
}

/**
 * 把「思考强度」翻译成各家请求体里要合并的额外字段。
 *
 * - openai    : reasoning_effort
 * - anthropic : thinking（budget_tokens 按档位给 8192/4096/2048）
 * - deepseek  : {}  R1 不需要传
 * - gemini    : {}  暂不实现
 * - unknown   : {}  不敢乱塞字段，避免被服务端 400
 *
 * effort 为 undefined（用户选了「关闭」）时一律返回空对象。
 */
export function reasoningEffortToParams(
  provider: ReasoningProvider,
  effort: ReasoningEffort | undefined
): Record<string, unknown> {
  if (!effort) return {};

  switch (provider) {
    case 'openai':
      return { reasoning_effort: effort };
    case 'anthropic':
      return {
        thinking: {
          type: 'enabled',
          budget_tokens: effort === 'high' ? 8192 : effort === 'medium' ? 4096 : 2048,
        },
      };
    case 'deepseek':
      return {};
    case 'gemini':
      return {};
    default:
      return {};
  }
}

export const apiClient = {
  /**
   * 发送一轮对话。
   *
   * onChunk 的入参是 { type, text }：`reasoning` 是思维链增量，`content` 是正文增量。
   * 调用方按 type 分别累加到消息的 reasoning / content 字段（见 useChatStore）。
   * 返回值为**正文**的完整拼接结果（不含思维链）。
   */
  async sendMessage(
    messages: Message[],
    config: APIConfig,
    onChunk?: (chunk: StreamChunk) => void,
    abortSignal?: AbortSignal,
    options?: { reasoningEffort?: ReasoningEffort }
  ): Promise<string> {
    // baseUrl 先归一化（去尾斜杠 + 去尾 /v1），再统一拼 /v1，避免用户自带 /v1 时出现 /v1/v1
    const url = normalizeBaseUrl(config.baseUrl) + "/v1" + API_ENDPOINTS.CHAT_COMPLETIONS;

    // 思考强度参数按 model 推断厂商后再 merge；unknown/关闭时为 {}，不影响原有字段
    // 取值优先用调用方显式传的 options，其次用 config 里的字段（两条路径都支持）
    const effort = options?.reasoningEffort ?? config.reasoningEffort;
    const reasoningParams = reasoningEffortToParams(
      detectReasoningProvider(config.model),
      effort
    );

    const body = {
      model: config.model,
      messages: messages.map((m) => ({ role: m.role, content: m.content })),
      max_tokens: config.maxTokens,
      temperature: config.temperature,
      stream: !!onChunk,
      ...reasoningParams,
    ...(config.model.toLowerCase().includes('agnes') ? { enable_thinking: true } : {}),
    };

    const response = await fetchWithRetry(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      'Accept': 'text/event-stream',
        Authorization: `Bearer ${config.apiKey}`,
      },
      body: JSON.stringify(body),
      signal: abortSignal,
    });

    if (!response.ok) {
      const text = await response.text().catch(() => '');
      throw new APIError(
        response.status,
        `API ${response.status}: ${text.slice(0, 200)}`
      );
    }

    if (onChunk && response.body) {
      return readSSEStream(response.body, onChunk);
    }

    const data = (await response.json()) as {
      choices?: {
        message?: { content?: string; reasoning_content?: string; reasoning?: string; thinking?: string };
      }[];
    };
    // 非流式分支没有 onChunk 通道回传思维链，返回值保持「正文」语义不变。
    // 当前所有对话都走流式；此分支只服务内部压缩这类无 UI 的调用。
    return data?.choices?.[0]?.message?.content || '';
  },
};

export { fetchWithRetry };
