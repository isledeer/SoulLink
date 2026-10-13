import React, { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useCharacterStore } from '../stores/useCharacterStore';
import { ImportCard } from '../components/ImportCard';
import { CharacterCard } from '../components/character/CharacterCard';
import { Button } from '../components/ui/Button';

export default function Home() {
  const navigate = useNavigate();
  const characters = useCharacterStore((s) => s.characters);
  const loadCharacters = useCharacterStore((s) => s.loadCharacters);

  useEffect(() => {
    loadCharacters();
  }, [loadCharacters]);

  return (
    <div className="min-h-screen bg-surface-100 dark:bg-surface-900 text-text-primary dark:text-surface-100">
      <header className="flex items-center justify-between p-4 border-b border-surface-200 dark:border-surface-700">
        <h1 className="text-lg font-bold">SoulLink</h1>
        <Button variant="ghost" size="sm" onClick={() => navigate('/settings')}>
          设置
        </Button>
      </header>

      <main className="p-4 space-y-4">
        <ImportCard />

        {characters.length === 0 ? (
          <div className="text-center py-12 text-text-secondary text-sm">
            还没有角色，点上方按钮导入酒馆卡
          </div>
        ) : (
          <div className="space-y-2">
            {characters.map((c) => (
              <CharacterCard
                key={c.id}
                character={c}
                onClick={() => navigate(`/character/${c.id}`)}
              />
            ))}
          </div>
        )}
      </main>
    </div>
  );
}
