import React, { useRef, useState } from 'react';

interface ChatInputProps {
  onSend: (message: string) => void;
  onStop?: () => void;
  isGenerating: boolean;
  disabled?: boolean;
}

/**
 * 胶囊输入栏 + 圆形发送按钮。
 *
 * 不复用 ui/Input 与 ui/Button：
 * - Input 外层有 <div className="w-full">，会吃掉 flex-1 的伸缩行为
 * - Button 的尺寸/布局是为通用场景特化的，这里按钮是固定圆形的 FAB
 */
export const ChatInput: React.FC<ChatInputProps> = ({
  onSend,
  onStop,
  isGenerating,
  disabled = false,
}) => {
  const [text, setText] = useState<string>('');
  const taRef = useRef<HTMLTextAreaElement>(null);

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
      className="shrink-0 border-t border-white/5 bg-surface-900/60 px-3 pt-2.5 pb-3 backdrop-blur-xl"
    >
      <div className="flex items-end gap-2">
        {/*
          胶囊外壳：边框 + 内部浅填充 + 聚焦光环。
          focus-within 让 textarea 拿到焦点时整圈亮起来。
        */}
        <div
          className="flex-1 min-w-0 rounded-[1.6rem] border border-white/10 bg-surface-800/70 px-4 py-2
                     transition-shadow duration-200
                     focus-within:border-primary-500/40 focus-within:ring-2 focus-within:ring-primary-500/25"
        >
          <textarea
            ref={taRef}
            rows={1}
            value={text}
            onChange={(e) => setText(e.target.value)}
            placeholder={isGenerating ? '生成中，可先输入下一条…' : '输入消息…'}
            disabled={disabled}
            className="block w-full resize-none bg-transparent text-[15px] leading-6
                       text-surface-100 placeholder:text-surface-300/45
                       focus:outline-none disabled:opacity-50 max-h-32 overflow-y-auto no-scrollbar"
          />
        </div>

        {isGenerating ? (
          <button
            type="button"
            onClick={() => onStop?.()}
            aria-label="停止生成"
            className="shrink-0 h-11 w-11 flex items-center justify-center rounded-full
                       border border-white/10 bg-surface-800 text-accent-500
                       hover:bg-surface-700 active:scale-95 transition-all"
          >
            <span className="block h-2.5 w-2.5 rounded-[2px] bg-current" aria-hidden="true" />
          </button>
        ) : (
          <button
            type="submit"
            disabled={!text.trim() || disabled}
            aria-label="发送"
            className="shrink-0 h-11 w-11 flex items-center justify-center rounded-full
                       bg-primary-500 text-white text-lg font-semibold
                       shadow-[0_0_22px_rgba(14,165,233,0.45)]
                       hover:bg-primary-600 active:scale-95 transition-all
                       disabled:opacity-35 disabled:shadow-none disabled:cursor-not-allowed"
          >
            ↑
          </button>
        )}
      </div>
    </form>
  );
};