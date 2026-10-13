export interface Message {
  id: string;
  role: 'system' | 'user' | 'assistant';
  content: string;
  timestamp: number;
  isStreaming?: boolean;
  /** 该消息是失败产生的，不参与 API 输入 */
  isError?: boolean;
  /** 被用户编辑过（本轮先加字段，暂不使用） */
  isEdited?: boolean;
}
