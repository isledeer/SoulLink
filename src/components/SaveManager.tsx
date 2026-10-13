import React, { useEffect, useState } from 'react';
import { useSaveStore } from '../stores/useSaveStore';

interface SaveManagerProps {
  characterId: string;
}

const ROW_BASE = 'p-3 rounded-xl border cursor-pointer transition-all';
const ROW_IDLE =
  'border-surface-200 hover:bg-surface-100 dark:border-surface-700 dark:hover:bg-surface-800';
const ROW_ACTIVE =
  'border-primary-500 bg-primary-50 dark:bg-primary-900/20 dark:border-primary-600';

export const SaveManager: React.FC<SaveManagerProps> = ({ characterId }) => {
  const saves = useSaveStore((s) => s.saves);
  const currentSaveId = useSaveStore((s) => s.currentSaveId);
  const loadSaves = useSaveStore((s) => s.loadSaves);
  const createSave = useSaveStore((s) => s.createSave);
  const switchSave = useSaveStore((s) => s.switchSave);
  const deleteSave = useSaveStore((s) => s.deleteSave);

  const [showInput, setShowInput] = useState(false);
  const [newName, setNewName] = useState('');

  useEffect(() => {
    loadSaves();
  }, [loadSaves]);

  const closeInput = () => {
    setShowInput(false);
    setNewName('');
  };

  const toggleInput = () => {
    if (showInput) closeInput();
    else setShowInput(true);
  };

  const handleCreate = async () => {
    const name = newName.trim();
    if (!name) return;

    const id = await createSave({ name, characterId });
    // createSave 只 set currentSaveId，不同步 chatStore.messages，
    // 必须显式 switchSave 才能让消息列表切到新存档（空数组）
    await switchSave(id);

    setNewName('');
    setShowInput(false);
  };

  const handleDelete = async (
    e: React.MouseEvent<HTMLButtonElement>,
    id: string
  ) => {
    // 必须先拦住冒泡，否则会连带触发整行的 switchSave
    e.stopPropagation();
    if (!window.confirm('确定删除该存档？')) return;
    await deleteSave(id);
  };

  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between">
        <h3 className="font-bold text-text-primary dark:text-surface-100">存档</h3>
        <button
          type="button"
          onClick={toggleInput}
          className="px-2 py-1 text-sm bg-primary-600 text-white rounded hover:bg-primary-700 active:scale-95 transition-all"
        >
          + 新建
        </button>
      </div>

      {showInput && (
        <div className="flex gap-2">
          <input
            autoFocus
            value={newName}
            onChange={(e) => setNewName(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') handleCreate();
              if (e.key === 'Escape') closeInput();
            }}
            className="flex-1 p-2 border border-surface-300 dark:border-surface-700 rounded bg-surface-0 dark:bg-surface-800 text-text-primary dark:text-surface-100 focus:outline-none focus:ring-2 focus:ring-primary-500"
          />
          <button
            type="button"
            disabled={!newName.trim()}
            onClick={handleCreate}
            className="px-3 py-2 text-sm bg-primary-600 text-white rounded hover:bg-primary-700 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            确认
          </button>
          <button
            type="button"
            onClick={closeInput}
            className="px-3 py-2 text-sm bg-surface-200 text-text-primary rounded hover:bg-surface-300 dark:bg-surface-700 dark:hover:bg-surface-800 dark:text-surface-100"
          >
            取消
          </button>
        </div>
      )}

      {saves.length === 0 ? (
        <p className="text-text-secondary text-sm text-center py-8">暂无存档</p>
      ) : (
        saves.map((save) => (
          <div
            key={save.id}
            onClick={() => switchSave(save.id)}
            className={`${ROW_BASE} ${
              save.id === currentSaveId ? ROW_ACTIVE : ROW_IDLE
            }`}
          >
            <div className="flex items-center justify-between gap-2">
              <div className="min-w-0">
                <div className="font-bold text-text-primary dark:text-surface-100 truncate">
                  {save.name}
                </div>
                <div className="text-xs text-text-secondary">
                  {new Date(save.updatedAt).toLocaleString()}
                </div>
              </div>
              <button
                type="button"
                onClick={(e) => handleDelete(e, save.id)}
                className="text-red-500 hover:text-red-700 text-sm shrink-0"
              >
                删除
              </button>
            </div>
          </div>
        ))
      )}
    </div>
  );
};
