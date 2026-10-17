export interface Message {
  id: string;
  role: 'system' | 'user' | 'assistant';
  content: string;
  timestamp: number;
  /**
   * 思维链原文（DeepSeek-R1 等模型流式返回的 reasoning_content）。
   * 刻意与 content 分开存：UI 用独立折叠面板展示，也不参与正文的 Markdown/KaTeX 渲染。
   * 历史消息里没有该字段时为 undefined，与空字符串等价。
   */
  reasoning?: string;
  isStreaming?: boolean;
  /** 该消息是失败产生的，不参与 API 输入 */
  isError?: boolean;
  /** 被用户编辑过（本轮先加字段，暂不使用） */
  isEdited?: boolean;
}
