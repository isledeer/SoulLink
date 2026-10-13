import React, { useRef, useState } from 'react';
import { parseTavernCardFromFile } from '../services/cardParser';
import { useCharacterStore } from '../stores/useCharacterStore';

interface ImportCardProps {
  onImported?: (characterId: string) => void;
}

export const ImportCard: React.FC<ImportCardProps> = ({ onImported }) => {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [isImporting, setIsImporting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const addCharacter = useCharacterStore((s) => s.addCharacter);

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    setIsImporting(true);
    setError(null);

    try {
      const card = await parseTavernCardFromFile(file);
      // 本轮不处理 avatar：file → blob URL 在 app 重启后会失效（裂图），
      // 且 blob URL 需手动 revoke 否则内存泄漏。
      // 头像存储方案（Blob + IndexedDB）标为 Phase 2。
      const id = await addCharacter(card);
      onImported?.(id);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setIsImporting(false);
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  };

  return (
    <div>
      <input
        ref={fileInputRef}
        type="file"
        accept="image/png"
        onChange={handleFileChange}
        className="hidden"
      />
      <button
        type="button"
        disabled={isImporting}
        onClick={() => fileInputRef.current?.click()}
        className="px-4 py-2 bg-primary-600 text-white rounded hover:bg-primary-700 active:scale-95 transition-all disabled:opacity-50 disabled:cursor-not-allowed"
      >
        {isImporting ? '导入中...' : '导入角色卡'}
      </button>
      {error && <p className="text-red-500 text-sm mt-2">{error}</p>}
    </div>
  );
};
