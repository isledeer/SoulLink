import React, { useState } from 'react';

interface ChatInputProps {
  onSend: (message: string) => void;
  onStop?: () => void;
  isGenerating: boolean;
  disabled?: boolean;
}

/**
 * 不复用 ui/Input 与 ui/Button：
 * - Input 外层有 <div className="w-full">，会吃掉 flex-1 的伸缩行为，
 *   这里需要 input 直接作为 flex 子项
 * - Button 的尺寸/布局是为通用场景特化的，这里按钮样式更贴合输入栏
 */
export const ChatInput: React.FC<ChatInputProps> = ({
  onSend,
  onStop,
  isGenerating,
  disabled = false,
}) => {
  const [text, setText] = useState<string>('');

  const handleSubmit = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();

    const trimmed = text.trim();
    if (!trimmed || isGenerating || disabled) return;

    onSend(trimmed);
    setText('');
  };

  return (
    <form
      onSubmit={handleSubmit}
      className="p-4 border-t border-surface-200 dark:border-surface-700 flex gap-2 bg-surface-0 dark:bg-surface-900"
    >
      <textarea rows={1}
        value={text}
        onChange={(e) => setText(e.target.value)}
        placeholder="输入消息..."
        disabled={isGenerating || disabled}
        className="flex-1 p-2 border border-surface-300 dark:border-surface-700 rounded bg-surface-0 dark:bg-surface-800 text-text-primary dark:text-surface-100 focus:outline-none focus:ring-2 focus:ring-primary-500 disabled:opacity-50 disabled:cursor-not-allowed resize-none max-h-32 overflow-y-auto"
      />
      {isGenerating ? (
        <button
          type="button"
          onClick={() => onStop?.()}
          className="px-4 py-2 bg-accent-500 text-white rounded hover:opacity-90 active:scale-95 transition-all"
        >
          停止
        </button>
      ) : (
        <button
          type="submit"
          disabled={!text.trim() || disabled}
          className="px-4 py-2 bg-primary-600 text-white rounded hover:bg-primary-700 active:scale-95 transition-all disabled:opacity-50 disabled:cursor-not-allowed"
        >
          发送
        </button>
      )}
    </form>
  );
};
