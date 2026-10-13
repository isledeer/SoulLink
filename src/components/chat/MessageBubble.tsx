import React from 'react';

interface MessageBubbleProps {
  content: string;
  isUser: boolean;
  isStreaming?: boolean;
  timestamp: number;
}

/**
 * 刻意不做打字机效果：
 * 本项目的打字机观感由「流式 chunk 陆续 appendStreamChunk 进 content」自然产生。
 * 若再叠一层 setInterval 打字机，会与 chunk 更新互相踩：每次 content 变化都要重置
 * 进度 index，导致字符被重复追加。这里只用一个跳动光标表示「正在生成」。
 */
export const MessageBubble: React.FC<MessageBubbleProps> = ({
  content,
  isUser,
  isStreaming = false,
  timestamp,
}) => {
  const timeStr = new Date(timestamp).toLocaleTimeString([], {
    hour: '2-digit',
    minute: '2-digit',
  });

  return (
    <div className={`flex w-full mb-4 ${isUser ? 'justify-end' : 'justify-start'}`}>
      <div
        className={`max-w-[75%] px-4 py-3 rounded-2xl text-sm leading-relaxed ${
          isUser
            ? 'bg-primary-600 text-white rounded-br-none'
            : 'bg-surface-200 text-text-primary rounded-bl-none dark:bg-surface-700 dark:text-surface-100'
        }`}
      >
        <p className="whitespace-pre-wrap break-words">
          {content}
          {isStreaming && (
            <span
              className="inline-block w-1 h-4 ml-1 bg-current animate-pulse align-text-bottom"
              aria-hidden="true"
            />
          )}
        </p>
        <div
          className={`text-[10px] mt-1 opacity-60 ${isUser ? 'text-right' : 'text-left'}`}
        >
          {timeStr}
        </div>
      </div>
    </div>
  );
};
