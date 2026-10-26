import React, { useEffect, useRef, useState } from 'react';
import { replacePlaceholders } from '../../utils/template';
import { MarkdownRenderer } from './MarkdownRenderer';

interface MessageBubbleProps {
  /** 消息 id：编辑/删除回调要带回去定位是哪一条 */
  id: string;
  content: string;
  /**
   * 思维链原文（DeepSeek-R1 等模型的 reasoning_content）。
   * 与 content 分开渲染：这里只出纯文本，绝不进 Markdown/KaTeX ——
   * 思维链里全是未闭合的记号，解析既卡又乱。
   */
  reasoning?: string;
  charName?: string;
  isUser: boolean;
  isStreaming?: boolean;
  timestamp: number;
  onEdit?: (id: string, newContent: string) => void;
  onDelete?: (id: string) => void;
}

/** 触摸长按多久算「唤出菜单」 */
const LONG_PRESS_MS = 500;

/**
 * 与 ui/Input 的视觉保持一致。
 * 不复用 Input 组件本身：它是 <input>，这里需要多行 <textarea>。
 */
const TEXTAREA_CLASSES =
  'w-full p-2 border rounded text-sm bg-surface-0 text-text-primary dark:bg-surface-800 dark:text-surface-100 ' +
  'border-surface-300 dark:border-surface-700 focus:outline-none focus:ring-2 focus:ring-primary-500';

const MENU_ITEM_CLASSES =
  'block w-full text-left px-3 py-2 text-sm text-text-primary dark:text-surface-100 ' +
  'hover:bg-surface-100 dark:hover:bg-surface-700 transition-colors';

/** 流式中的跳动光标。正文与思维链各用一次，语义保持一致 */
const CARET_CLASSES =
  'inline-block w-1 h-3.5 ml-1 bg-current animate-pulse align-text-bottom';

/**
 * 思维链折叠面板（「思考过程」）。
 *
 * 为什么不用原生 <details>：折叠本身虽然可以由浏览器维护，但「流式结束瞬间自动折叠」
 * 是**程序化**的 —— 用原生标签就得拿 DOM 引用去改 open 属性，绕开 React 的状态模型。
 * 这里用 useState + button，把折叠状态完整交给 React。
 *
 * 状态机：
 * - isStreaming false→true（开始生成）→ 自动展开，思维链实时滚动
 * - isStreaming true→false（生成结束）→ 自动折叠，标题变「已深度思考（点击展开）」
 * - 用户在这两次切换之间手动开合 → 保持不动，与流式状态解耦
 *
 * 面板内容刻意只做纯文本：思维链里全是没闭合的 markdown/LaTeX 记号，
 * 交给 MarkdownRenderer 既卡主线程，也会渲染出一堆中间态。
 */
const ReasoningPanel: React.FC<{ reasoning: string; isStreaming: boolean }> = ({
  reasoning,
  isStreaming,
}) => {
  const [open, setOpen] = useState(isStreaming);
  const prevStreaming = useRef(isStreaming);

  useEffect(() => {
    if (prevStreaming.current === isStreaming) return;
    prevStreaming.current = isStreaming;
    setOpen(isStreaming);
  }, [isStreaming]);

  return (
    <div className="mb-2 rounded-lg overflow-hidden border border-surface-300 dark:border-surface-700 bg-surface-0 dark:bg-surface-800">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
        className="w-full flex items-center justify-between gap-2 px-3 py-1.5 text-left hover:bg-surface-100 dark:hover:bg-surface-700 transition-colors"
      >
        {isStreaming ? (
          <span className="inline-flex items-center gap-1.5 text-xs text-text-secondary dark:text-surface-300">
            <span
              className="inline-block w-1.5 h-1.5 rounded-full bg-primary-500 animate-pulse"
              aria-hidden="true"
            />
            思考中...
          </span>
        ) : (
          <span className="text-xs text-text-secondary dark:text-surface-300">
            已深度思考（点击展开）
          </span>
        )}
        <span className="text-[10px] shrink-0 text-text-secondary dark:text-surface-300">
          {open ? '收起' : '展开'}
        </span>
      </button>

      {open && (
        <p className="px-3 pb-2 pt-0.5 text-xs italic whitespace-pre-wrap break-words text-text-secondary dark:text-surface-300">
          {reasoning}
          {isStreaming && <span className={CARET_CLASSES} aria-hidden="true" />}
        </p>
      )}
    </div>
  );
};

/**
 * 刻意不做打字机效果：
 * 本项目的打字机观感由「流式 chunk 陆续 appendStreamChunk 进 content」自然产生。
 * 若再叠一层 setInterval 打字机，会与 chunk 更新互相踩：每次 content 变化都要重置
 * 进度 index，导致字符被重复追加。这里只用一个跳动光标表示「正在生成」。
 *
 * 长按/右键菜单：桌面走 onContextMenu，移动端走 500ms 触摸长按。
 * 两者都只是「打开菜单」，真正的动作在菜单项里。
 */
export const MessageBubble: React.FC<MessageBubbleProps> = ({
  id,
  content,
  reasoning,
  charName,
  isUser,
  isStreaming = false,
  timestamp,
  onEdit,
  onDelete,
}) => {
  const timeStr = new Date(timestamp).toLocaleTimeString([], {
    hour: '2-digit',
    minute: '2-digit',
  });

  const [menuOpen, setMenuOpen] = useState(false);
  const [isEditing, setIsEditing] = useState(false);
  const [draft, setDraft] = useState(content);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [copied, setCopied] = useState(false);

  const rootRef = useRef<HTMLDivElement>(null);
  const pressTimer = useRef<number | null>(null);
  const copyHintTimer = useRef<number | null>(null);

  const clearPressTimer = () => {
    if (pressTimer.current !== null) {
      window.clearTimeout(pressTimer.current);
      pressTimer.current = null;
    }
  };

  const openMenu = () => {
    setMenuOpen(true);
    setConfirmDelete(false);
  };

  const closeMenu = () => {
    setMenuOpen(false);
    setConfirmDelete(false);
  };

  // 组件卸载时清掉所有在途定时器，避免对着已卸载组件 setState
  useEffect(() => {
    return () => {
      if (pressTimer.current !== null) window.clearTimeout(pressTimer.current);
      if (copyHintTimer.current !== null) window.clearTimeout(copyHintTimer.current);
    };
  }, []);

  // 点击/触摸菜单外部关闭菜单（不拦 Esc 之外的按键，交给各控件自己处理）
  useEffect(() => {
    if (!menuOpen) return;

    const handleOutside = (e: MouseEvent | TouchEvent) => {
      if (rootRef.current && !rootRef.current.contains(e.target as Node)) {
        closeMenu();
      }
    };

    document.addEventListener('mousedown', handleOutside);
    document.addEventListener('touchstart', handleOutside);
    return () => {
      document.removeEventListener('mousedown', handleOutside);
      document.removeEventListener('touchstart', handleOutside);
    };
  }, [menuOpen]);

  const handleTouchStart = () => {
    clearPressTimer();
    pressTimer.current = window.setTimeout(() => {
      pressTimer.current = null;
      openMenu();
    }, LONG_PRESS_MS);
  };

  const handleCopy = async () => {
    try {
      if (navigator.clipboard) {
        await navigator.clipboard.writeText(content);
      } else {
        throw new Error('clipboard unavailable');
      }
    } catch {
      // 非安全上下文 / 无权限时的兜底：老式 execCommand 复制
      const helper = document.createElement('textarea');
      helper.value = content;
      helper.style.position = 'fixed';
      helper.style.opacity = '0';
      document.body.appendChild(helper);
      helper.select();
      try {
        document.execCommand('copy');
      } catch {
        // 复制彻底失败也不打扰用户，静默忽略
      }
      document.body.removeChild(helper);
    }

    setCopied(true);
    if (copyHintTimer.current !== null) window.clearTimeout(copyHintTimer.current);
    copyHintTimer.current = window.setTimeout(() => setCopied(false), 1500);

    closeMenu();
  };

  const handleStartEdit = () => {
    setDraft(content);
    setIsEditing(true);
    closeMenu();
  };

  const handleSaveEdit = () => {
    const trimmed = draft.trim();
    // 空内容不允许保存：那等于把消息删成空壳，用删除更合适
    if (!trimmed) return;

    onEdit?.(id, trimmed);
    setIsEditing(false);
  };

  const handleCancelEdit = () => {
    setDraft(content);
    setIsEditing(false);
  };

  const handleConfirmDelete = () => {
    setConfirmDelete(false);
    closeMenu();
    onDelete?.(id);
  };

  // {{char}} → 角色名。自由对话下 charName 恒为 'isledeer'，替换照常执行
  const displayContent = charName
    ? replacePlaceholders(content, charName, '你')
    : content;

  return (
    <div className={`flex w-full mb-3 ${isUser ? 'justify-end' : 'justify-start'}`}>
      <div
        ref={rootRef}
        onContextMenu={(e) => {
          e.preventDefault();
          openMenu();
        }}
        onTouchStart={handleTouchStart}
        onTouchEnd={clearPressTimer}
        onTouchMove={clearPressTimer}
        onTouchCancel={clearPressTimer}
        className={`relative max-w-[78%] px-4 py-2.5 text-[15px] leading-[1.75] ${
          isUser
            ? 'bubble-user bg-primary-600 text-white rounded-2xl rounded-br-md'
            : 'rounded-2xl rounded-bl-md bg-surface-800/75 text-surface-100 backdrop-blur-sm'
        }`}
      >
        {/**
          * 思维链面板放在正文气泡**上方**，编辑态下不显示：
          * 编辑改的是正文，此时把思维链摊在旁边只会让人分心。
          */}
        {reasoning && !isEditing && (
          <ReasoningPanel reasoning={reasoning} isStreaming={isStreaming} />
        )}

        {isEditing ? (
          <div className="min-w-[12rem]">
            <textarea
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              rows={3}
              autoFocus
              className={TEXTAREA_CLASSES}
            />
            <div className="flex gap-2 mt-2">
              <button
                type="button"
                onClick={handleSaveEdit}
                className="px-3 py-1 rounded text-xs font-medium bg-primary-600 text-white hover:bg-primary-700 active:scale-95 transition-all"
              >
                保存
              </button>
              <button
                type="button"
                onClick={handleCancelEdit}
                className="px-3 py-1 rounded text-xs font-medium bg-surface-0 text-text-primary border border-surface-300 hover:bg-surface-200 dark:bg-surface-800 dark:text-surface-100 dark:border-surface-700 dark:hover:bg-surface-700 active:scale-95 transition-all"
              >
                取消
              </button>
            </div>
          </div>
        ) : isStreaming ? (
          /**
           * 流式中**绝对不渲染 MarkdownRenderer**：此刻 markdown 还不完整
           * （三反引号 / $$ 只有一半），每来一个 chunk 就把整段交给 remark/rehype
           * + KaTeX 重新解析，长回复下主线程会被解析吃满，表现就是「一次性卡死」。
           * 这里退化成纯文本节点，代价只是没有样式，换来的是逐字平滑。
           *
           * 等 finalizeStream 把 isStreaming 置 false，同一次渲染里自然切回
           * MarkdownRenderer，不需要额外状态。
           */
          <p className="whitespace-pre-wrap break-words">
            {displayContent}
            <span className={CARET_CLASSES} aria-hidden="true" />
          </p>
        ) : (
          <MarkdownRenderer content={displayContent} />
        )}

        <div
          className={`text-[10px] mt-1 opacity-60 ${isUser ? 'text-right' : 'text-left'}`}
        >
          {timeStr}
          {copied && <span className="ml-2">已复制</span>}
        </div>

        {menuOpen && (
          <div
            className="absolute right-0 top-full mt-1 z-20 min-w-[8rem] py-1 rounded-lg shadow-lg
                       bg-surface-0 dark:bg-surface-800
                       border border-surface-200 dark:border-surface-700"
            role="menu"
          >
            {confirmDelete ? (
              <div className="px-3 py-2">
                <p className="text-xs mb-2 text-text-secondary dark:text-surface-300">
                  确认删除这条消息？
                </p>
                <div className="flex gap-2">
                  <button
                    type="button"
                    onClick={handleConfirmDelete}
                    className="px-3 py-1 rounded text-xs font-medium bg-accent-500 text-white hover:opacity-90 active:scale-95 transition-all"
                  >
                    删除
                  </button>
                  <button
                    type="button"
                    onClick={() => setConfirmDelete(false)}
                    className="px-3 py-1 rounded text-xs font-medium bg-surface-200 text-text-primary hover:bg-surface-300 dark:bg-surface-700 dark:text-surface-100 dark:hover:bg-surface-800 active:scale-95 transition-all"
                  >
                    取消
                  </button>
                </div>
              </div>
            ) : (
              <>
                <button type="button" role="menuitem" onClick={handleCopy} className={MENU_ITEM_CLASSES}>
                  复制
                </button>
                <button type="button" role="menuitem" onClick={handleStartEdit} className={MENU_ITEM_CLASSES}>
                  编辑
                </button>
                <button
                  type="button"
                  role="menuitem"
                  onClick={() => setConfirmDelete(true)}
                  className={`${MENU_ITEM_CLASSES} text-accent-500`}
                >
                  删除
                </button>
              </>
            )}
          </div>
        )}
      </div>
    </div>
  );
};
