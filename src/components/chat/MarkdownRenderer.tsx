import React, { createContext, useContext, useState } from 'react';
import ReactMarkdown from 'react-markdown';
import type { ExtraProps } from 'react-markdown';
import remarkGfm from 'remark-gfm';
import remarkMath from 'remark-math';
import rehypeKatex from 'rehype-katex';
import 'katex/dist/katex.min.css';

interface MarkdownRendererProps {
  content: string;
}

/**
 * 「当前 code 是否位于代码块内」。
 *
 * 块级代码在 markdown 里必然被 <pre> 包着，而行内代码不会 —— 用 context 传这个事实，
 * 比靠「className 有没有 language-」「children 里有没有换行」猜要可靠：
 * 无语言标注的围栏代码块两者都判不准。
 */
const InCodeBlockContext = createContext(false);

/**
 * 代码块右上角的复制按钮。
 *
 * 与 MessageBubble 的「复制整条消息」是两回事：这里只复制这个代码块的源码。
 * clipboard API 在非安全上下文（如打包进 APK 后的 file:// WebView）可能整个不存在，
 * 所以保留 execCommand 兜底 —— 与 MessageBubble 的处理保持一致。
 */
const CodeCopyButton: React.FC<{ text: string }> = ({ text }) => {
  const [copied, setCopied] = useState(false);

  const handleCopy = async () => {
    try {
      if (navigator.clipboard) {
        await navigator.clipboard.writeText(text);
      } else {
        throw new Error('clipboard unavailable');
      }
    } catch {
      // 兜底：老式 execCommand 复制
      const helper = document.createElement('textarea');
      helper.value = text;
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
    window.setTimeout(() => setCopied(false), 1500);
  };

  return (
    <button
      type="button"
      onClick={handleCopy}
      aria-label="复制代码"
      className="absolute right-2 top-2 z-10 px-2 py-0.5 rounded text-[10px] font-medium
                 bg-primary-600 text-white hover:bg-primary-700
                 active:scale-95 transition-all"
    >
      {copied ? '已复制' : '复制'}
    </button>
  );
};

/** 从 React 子树里抠出纯文本（代码块的复制内容要用它） */
function extractText(node: React.ReactNode): string {
  if (node === null || node === undefined || typeof node === 'boolean') return '';
  if (typeof node === 'string') return node;
  if (typeof node === 'number') return String(node);
  if (Array.isArray(node)) return node.map(extractText).join('');
  if (React.isValidElement(node)) {
    return extractText((node.props as { children?: React.ReactNode }).children);
  }
  return '';
}

/**
 * 行内代码：小背景 + 项目色板，不套 <pre>。
 * 块级代码同样走这里，但由外层 <pre> 的 context 判定，只输出朴素的 <code>。
 *
 * ExtraProps 里只有 node，绝不能 {...rest} 展开到 DOM 上 ——
 * 那会在 <code> 上留下 node="[object Object]" 这种脏属性（实测会出现）。
 */
const CustomCode: React.FC<React.HTMLAttributes<HTMLElement> & ExtraProps> = ({
  className,
  children,
}) => {
  const inCodeBlock = useContext(InCodeBlockContext);

  if (inCodeBlock) {
    return <code className={className}>{children}</code>;
  }

  return (
    <code
      className="px-1.5 py-0.5 rounded text-[0.85em] font-mono break-words
                 bg-surface-200 text-text-primary
                 dark:bg-surface-800 dark:text-surface-100"
    >
      {children}
    </code>
  );
};

/**
 * 块级代码容器。
 *
 * 刻意接管 <pre>：复制按钮要相对它定位，且必须阻止 react-markdown 默认的
 * <pre> 再套一层（否则会变成 <pre><pre>，内层实测会多出一块顶部空白）。
 *
 * 同样不展开 {...rest}：ExtraProps 的 node 不是合法 DOM 属性。
 *
 * 语法高亮留给 B-2，本轮只做样式 + 复制。
 */
const CustomPre: React.FC<React.HTMLAttributes<HTMLPreElement> & ExtraProps> = ({
  children,
}) => {
  const text = extractText(children).replace(/\n$/, '');

  return (
    <InCodeBlockContext.Provider value={true}>
      <pre
        className="relative my-2 p-3 pr-14 rounded-lg overflow-x-auto
                   bg-surface-800 text-surface-100
                   dark:bg-surface-900 text-xs leading-relaxed"
      >
        <CodeCopyButton text={text} />
        {children}
      </pre>
    </InCodeBlockContext.Provider>
  );
};

/**
 * 「整行就是一个 $$...$$」→ 展开成独占一行的块级公式。
 *
 * remark-math 只有在 `$$` **独占一行**时才产出 math（display）节点；
 * 写成 `$$x^2$$` 会被判成 inlineMath，rehype-katex 自然也就不会加 katex-display，
 * 结果块级公式被塞在 <p> 里居中不了。模型输出单行 $$...$$ 极常见，这里补一刀。
 *
 * 刻意只认「整行仅含一对 $$」：行内混排（`前文 $$x$$ 后文`）或一行多对
 * （`$$a$$ and $$b$$`）一律不动，避免把本该行内的公式误升级成块级。
 */
const SINGLE_LINE_DISPLAY_MATH = /^[ \t]*\$\$([^\n$]+?)\$\$[ \t]*$/gm;

function normalizeDisplayMath(content: string): string {
  return content.replace(
    SINGLE_LINE_DISPLAY_MATH,
    (_match, body: string) => `$$\n${body.trim()}\n$$`
  );
}

/**
 * 消息正文的 Markdown + LaTeX 渲染。
 *
 * 安全：刻意不开 rehype-raw，也没有 allowDangerousHtml 这类开关，
 * 保持 react-markdown 默认行为 —— 模型输出里的 raw HTML 一律按纯文本渲染，
 * <script> / onerror 之类不会被执行。
 */
export const MarkdownRenderer: React.FC<MarkdownRendererProps> = ({ content }) => {
  return (
    <div className="markdown-body break-words">
      <ReactMarkdown
        remarkPlugins={[remarkGfm, remarkMath]}
        rehypePlugins={[rehypeKatex]}
        components={{ code: CustomCode, pre: CustomPre }}
      >
        {normalizeDisplayMath(content)}
      </ReactMarkdown>
    </div>
  );
};