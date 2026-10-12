import type { Message } from '../types/message';

export const COMPRESSION_THRESHOLD = 40;
export const KEEP_RECENT = 10;

const COMPRESS_PROMPT_ID = 'compress-prompt';
const SYSTEM_CHARACTER_ID = 'system-character';
const SYSTEM_SUMMARY_ID = 'system-memory-summary';

const COMPRESS_PROMPT_CONTENT =
  '请将以下对话历史总结为一段简洁的第三人称叙述，保留关键事件、情感变化和重要细节。';

/** 消息数超过阈值才需要压缩 */
export function shouldCompress(messages: Message[]): boolean {
  return messages.length > COMPRESSION_THRESHOLD;
}

/**
 * 把较早的对话交给 apiCall 总结，保留最近 KEEP_RECENT 条原文。
 * apiCall 由调用方注入（通常是 apiClient.sendMessage 的包装），便于测试与复用。
 */
export async function compressMessages(
  messages: Message[],
  apiCall: (msgs: Message[]) => Promise<string>
): Promise<{ summary: string; recentMessages: Message[] }> {
  const toCompress = messages.slice(0, messages.length - KEEP_RECENT);
  const recent = messages.slice(-KEEP_RECENT);

  const promptMessages: Message[] = [
    {
      id: COMPRESS_PROMPT_ID,
      role: 'system',
      content: COMPRESS_PROMPT_CONTENT,
      timestamp: Date.now(),
    },
    ...toCompress,
  ];

  const summary = await apiCall(promptMessages);

  return { summary, recentMessages: recent };
}

export interface CharacterMetadataForContext {
  name: string;
  description?: string;
  personality?: string;
  scenario?: string;
  first_mes?: string;
  system_prompt?: string;
  post_history_instructions?: string;
}

/**
 * 组装发给模型的上下文：
 * 1. 固定的角色设定 system 消息
 * 2. 可选的记忆摘要 system 消息
 * 3. 原历史里的非 system 消息（原样保留 id/role/content/timestamp/isStreaming）
 */
export function buildContextWithSummary(
  messages: Message[],
  characterMetadata: CharacterMetadataForContext,
  compressionSummary: string | null
): Message[] {
  const characterContent = [
    characterMetadata.system_prompt,
    `角色名：${characterMetadata.name}`,
    characterMetadata.description && `角色描述：${characterMetadata.description}`,
    characterMetadata.personality && `性格：${characterMetadata.personality}`,
    characterMetadata.scenario && `场景：${characterMetadata.scenario}`,
    characterMetadata.first_mes && `开场白：${characterMetadata.first_mes}`,
    characterMetadata.post_history_instructions,
  ]
    .filter(Boolean)
    .join('\n');

  const context: Message[] = [
    {
      id: SYSTEM_CHARACTER_ID,
      role: 'system',
      content: characterContent,
      timestamp: Date.now(),
    },
  ];

  if (compressionSummary) {
    context.push({
      id: SYSTEM_SUMMARY_ID,
      role: 'system',
      content: `【记忆摘要】\n${compressionSummary}`,
      timestamp: Date.now(),
    });
  }

  // 原历史里的 system 一律丢弃，避免与上面两条重复
  for (const message of messages) {
    if (message.role === 'system') continue;
    context.push(message);
  }

  return context;
}
