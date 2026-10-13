import React, { useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useCharacterStore } from '../stores/useCharacterStore';
import { CharacterInfoPanel } from '../components/CharacterInfoPanel';
import { Button } from '../components/ui/Button';

/**
 * 角色详情页。router/index.tsx 里 `/character/:id` 指向本组件，
 * 复用既有的 CharacterInfoPanel 渲染角色设定。
 */
export default function CharacterDetail() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();

  const character = useCharacterStore((s) => s.characters.find((c) => c.id === id));
  const loadCharacters = useCharacterStore((s) => s.loadCharacters);

  useEffect(() => {
    loadCharacters();
  }, [loadCharacters]);

  if (!character) {
    return (
      <div className="flex flex-col items-center justify-center min-h-screen gap-4 bg-surface-100 dark:bg-surface-900">
        <p className="text-text-secondary">角色未找到</p>
        <Button onClick={() => navigate('/')}>返回首页</Button>
      </div>
    );
  }

  return (
    <div className="flex flex-col h-screen bg-surface-100 dark:bg-surface-900">
      <header className="flex items-center gap-2 p-3 border-b border-surface-200 dark:border-surface-700">
        <Button variant="ghost" size="sm" onClick={() => navigate('/')}>
          ← 返回
        </Button>
        <h1 className="flex-1 font-semibold truncate text-text-primary dark:text-surface-100">
          角色详情
        </h1>
      </header>

      <div className="flex-1 overflow-hidden">
        <CharacterInfoPanel
          name={character.data.name}
          description={character.data.description}
          personality={character.data.personality}
          scenario={character.data.scenario}
          firstMes={character.data.first_mes}
          mesExample={character.data.mes_example}
          avatar={character.avatar}
        />
      </div>

      <div className="p-4 border-t border-surface-200 dark:border-surface-700">
        <Button className="w-full" onClick={() => navigate(`/chat/${character.id}`)}>
          开始对话
        </Button>
      </div>
    </div>
  );
}
