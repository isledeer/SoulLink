import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useCharacterStore } from '../stores/useCharacterStore';
import { FREE_CHAT_SAVE_NAME, useSaveStore } from '../stores/useSaveStore';
import { ImportCard } from '../components/ImportCard';
import { CharacterCard } from '../components/character/CharacterCard';
import { Button } from '../components/ui/Button';

/** 自由对话在路由里占用的 :characterId 段（Chat 页把它归一化为 null） */
const FREE_CHAT_CHARACTER_ID = 'free';

export default function Home() {
  const navigate = useNavigate();
  const characters = useCharacterStore((s) => s.characters);
  const loadCharacters = useCharacterStore((s) => s.loadCharacters);

  const [isCreatingFreeChat, setIsCreatingFreeChat] = useState(false);

  useEffect(() => {
    loadCharacters();
  }, [loadCharacters]);

  /**
   * 新建自由对话：不导入任何角色卡，以内置 isledeer 身份开聊。
   *
   * 这里先建档再跳转，是为了让「存档列表」立刻能看到它；
   * Chat 页的初始化逻辑对自由对话同样精确匹配 characterId === null，
   * 找到这条已存在的档就直接 switchSave，不会重复建档。
   */
  const handleCreateFreeChat = async () => {
    if (isCreatingFreeChat) return;
    setIsCreatingFreeChat(true);

    try {
      const id = await useSaveStore.getState().createSave({
        characterId: null,
        name: FREE_CHAT_SAVE_NAME,
      });
      await useSaveStore.getState().switchSave(id);
      navigate(`/chat/${FREE_CHAT_CHARACTER_ID}`);
    } finally {
      setIsCreatingFreeChat(false);
    }
  };

  return (
    <div className="min-h-screen bg-surface-100 dark:bg-surface-900 text-text-primary dark:text-surface-100">
      <header className="flex items-center justify-between p-4 border-b border-surface-200 dark:border-surface-700">
        <h1 className="text-lg font-bold">SoulLink</h1>
        <Button variant="ghost" size="sm" onClick={() => navigate('/settings')}>
          设置
        </Button>
      </header>

      <main className="p-4 space-y-4">
        <div className="flex flex-wrap items-center gap-2">
          <ImportCard />
          <Button
            variant="secondary"
            onClick={handleCreateFreeChat}
            disabled={isCreatingFreeChat}
          >
            {isCreatingFreeChat ? '创建中...' : '新建通用对话'}
          </Button>
        </div>

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
