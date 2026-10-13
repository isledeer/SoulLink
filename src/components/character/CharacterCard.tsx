import React from 'react';
import type { CharacterRecord } from '../../stores/useCharacterStore';

interface CharacterCardProps {
  character: CharacterRecord;
  onClick: () => void;
}

export const CharacterCard: React.FC<CharacterCardProps> = ({ character, onClick }) => {
  const { name, description } = character.data;

  return (
    <div
      onClick={onClick}
      className="flex items-center gap-3 p-3 rounded-xl border cursor-pointer transition-all border-surface-200 hover:bg-surface-100 dark:border-surface-700 dark:hover:bg-surface-800"
    >
      {character.avatar ? (
        <img
          src={character.avatar}
          alt={name}
          className="w-12 h-12 rounded-full object-cover"
        />
      ) : (
        <div className="w-12 h-12 rounded-full bg-primary-600 text-white flex items-center justify-center text-lg font-bold">
          {name.charAt(0).toUpperCase()}
        </div>
      )}

      <div className="flex-1 min-w-0">
        <div className="font-semibold text-text-primary dark:text-surface-100 truncate">
          {name}
        </div>
        <div className="text-xs text-text-secondary line-clamp-1">
          {description ? description.slice(0, 60) : '暂无描述'}
        </div>
      </div>
    </div>
  );
};
