import React from 'react';

interface ModalProps {
  isOpen: boolean;
  onClose: () => void;
  title: string;
  children: React.ReactNode;
}

export const Modal: React.FC<ModalProps> = ({ isOpen, onClose, title, children }) => {
  const titleId = React.useId();

  // hooks 必须在提前 return 之前调用，否则 isOpen 切换会打乱 hooks 顺序。
  // 只在打开时注册 keydown，关闭/卸载时由 cleanup 摘掉。
  React.useEffect(() => {
    if (!isOpen) return;

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };

    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm"
      onClick={onClose}
      role="presentation"
    >
      <div
        className="bg-surface-0 dark:bg-surface-800 rounded-lg shadow-xl w-full max-w-md p-6 max-h-[85vh] overflow-y-auto"
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
      >
        <div className="flex items-start justify-between mb-4">
          <h2
            id={titleId}
            className="text-lg font-bold text-text-primary dark:text-surface-100"
          >
            {title}
          </h2>
          <button
            type="button"
            onClick={onClose}
            aria-label="关闭"
            className="text-text-secondary hover:text-text-primary dark:text-surface-100 leading-none text-2xl"
          >
            ×
          </button>
        </div>
        {children}
      </div>
    </div>
  );
};
