import { AppError, APIError, NetworkError } from './errorHandler';
import { API_ENDPOINTS } from '../constants';
import type { APIConfig } from '../types/api';
import type { Message } from '../types/message';

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
  throw new NetworkError('Request failed after retries');
}

/** 解析单个 SSE event，返回其中拼接出的文本增量（解析失败返回空串） */
function parseSSEEvent(event: string): string {
  let content = '';

  for (const line of event.split('\n')) {
    const trimmed = line.trim();
    if (!trimmed.startsWith('data:')) continue;

    const data = trimmed.slice('data:'.length).trim();
    if (!data || data === '[DONE]') continue;

    try {
      const parsed = JSON.parse(data) as {
        choices?: { delta?: { content?: string } }[];
      };
      const delta = parsed?.choices?.[0]?.delta?.content;
      if (delta) content += delta;
    } catch {
      // 单个 event 解析失败静默跳过，不影响后续 event
    }
  }

  return content;
}

/** 流式读取 SSE 响应体，逐块回调并返回完整文本 */
async function readSSEStream(
  body: ReadableStream<Uint8Array>,
  onChunk: (chunk: string) => void
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
      const chunk = parseSSEEvent(event);
      if (chunk) {
        fullContent += chunk;
        onChunk(chunk);
      }
    }
  }

  // 冲刷解码器与残留 buffer：部分服务端最后一个 event 不带结尾空行
  buffer += decoder.decode();
  const tail = parseSSEEvent(buffer);
  if (tail) {
    fullContent += tail;
    onChunk(tail);
  }

  return fullContent;
}

export const apiClient = {
  async sendMessage(
    messages: Message[],
    config: APIConfig,
    onChunk?: (chunk: string) => void,
    abortSignal?: AbortSignal
  ): Promise<string> {
    // 去掉 baseUrl 末尾斜杠后拼接，不替用户补 /v1（用户可能已经自带）
    const url = config.baseUrl.replace(/\/+$/, '') + API_ENDPOINTS.CHAT_COMPLETIONS;

    const body = {
      model: config.model,
      messages: messages.map((m) => ({ role: m.role, content: m.content })),
      max_tokens: config.maxTokens,
      temperature: config.temperature,
      stream: !!onChunk,
    };

    const response = await fetchWithRetry(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
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
      choices?: { message?: { content?: string } }[];
    };
    return data?.choices?.[0]?.message?.content || '';
  },
};

export { fetchWithRetry };
