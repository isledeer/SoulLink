import React, { useEffect, useRef, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useChatStore } from '../stores/useChatStore';
import { useSaveStore } from '../stores/useSaveStore';
import { useCharacterStore } from '../stores/useCharacterStore';
import { MessageBubble } from '../components/chat/MessageBubble';
import { ChatInput } from '../components/chat/ChatInput';
import { SaveManager } from '../components/SaveManager';
import { Modal } from '../components/ui/Modal';
import { Button } from '../components/ui/Button';
import { generateId } from '../utils/helpers';
import { getAffectionLevel } from '../systems/AffectionSystem';
import type { AffectionState } from '../systems/AffectionSystem';
import type { Message } from '../types/message';

/** 关系等级 → 中文标签（与 AffectionSystem.getAffectionLevel 的四个档位一一对应） */
const LEVEL_LABELS: Record<AffectionState['level'], string> = {
  stranger: '陌生',
  friend: '朋友',
  close: '亲密',
  lover: '恋人',
};

export default function Chat() {
  const { characterId } = useParams<{ characterId: string }>();
  const navigate = useNavigate();

  const character = useCharacterStore((s) =>
    s.characters.find((c) => c.id === characterId)
  );

  // 直接订阅，供自动保存 effect 的依赖数组使用
  const messages = useChatStore((s) => s.messages);
  const isSending = useChatStore((s) => s.isSending);

  // 顶部好感度显示：跟随当前存档的 gameState
  const currentSave = useSaveStore((s) =>
    s.saves.find((x) => x.id === s.currentSaveId)
  );
  const affinity = currentSave?.gameState.affinity ?? 0;
  const level = getAffectionLevel(affinity);
  const levelLabel = LEVEL_LABELS[level];

  // debug 视图：AI 最后一条回复的原始 fullContent（含 <game> 块）
  const lastRawContent = useChatStore((s) => s.lastRawContent);
  // debug 视图：实际发给模型的 messages 数组（诊断 first_mes 重复问题）
  const lastApiMessages = useChatStore((s) => s.lastApiMessages);

  const [showSaves, setShowSaves] = useState(false);
  const [showDebug, setShowDebug] = useState(false);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  // 把当前角色告诉 chatStore：sendMessage 注入 system prompt 时要用。
  // 卸载时清空，避免离开 Chat 页后残留上一个角色的上下文。
  useEffect(() => {
    useChatStore.getState().setCurrentCharacterId(characterId ?? null);
    return () => useChatStore.getState().setCurrentCharacterId(null);
  }, [characterId]);

  // 初始化：拉角色与存档，决定切档还是建新档。
  // 内部统一用 getState() 取最新值，避免依赖数组带来的陈旧闭包。
  useEffect(() => {
    if (!characterId) return;
    let cancelled = false;

    (async () => {
      await useCharacterStore.getState().loadCharacters();
      await useSaveStore.getState().loadSaves();
      if (cancelled) return;

      const saves = useSaveStore.getState().saves;
      const charSaves = saves
        .filter((s) => s.characterId === characterId)
        .sort((a, b) => b.updatedAt - a.updatedAt);

      if (charSaves.length > 0) {
        await useSaveStore.getState().switchSave(charSaves[0].id);
      } else {
        const char = useCharacterStore.getState().getCharacterById(characterId);
        if (!char) return;

        const initialMessages: Message[] = char.data.first_mes
          ? [
              {
                id: generateId(),
                role: 'assistant',
                content: char.data.first_mes,
                timestamp: Date.now(),
              },
            ]
          : [];

        const id = await useSaveStore.getState().createSave({
          name: char.data.name,
          characterId,
          messages: initialMessages,
        });
        await useSaveStore.getState().switchSave(id);
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [characterId]);

  // 自动保存：流式结束后把消息落库
  useEffect(() => {
    const currentSaveId = useSaveStore.getState().currentSaveId;
    if (!currentSaveId) return;
    if (isSending || messages.length === 0) return;

    useSaveStore
      .getState()
      .updateSave(currentSaveId, { messages })
      .catch(() => {});
  }, [messages, isSending]);

  // 新消息滚到底部
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSend = (content: string) => {
    useChatStore.getState().sendMessage(content);
  };

  const handleStop = () => {
    useChatStore.getState().abort();
  };

  if (!character) {
    return (
      <div className="flex flex-col items-center justify-center min-h-screen gap-4">
        <p className="text-text-secondary">角色未找到</p>
        <Button onClick={() => navigate('/')}>返回首页</Button>
      </div>
    );
  }

  return (
    <div className="flex flex-col h-screen bg-surface-0 dark:bg-surface-900">
      <header className="flex items-center gap-2 p-3 border-b border-surface-200 dark:border-surface-700">
        <Button variant="ghost" size="sm" onClick={() => navigate('/')}>
          ← 返回
        </Button>
        <h1 className="flex-1 font-semibold truncate text-text-primary dark:text-surface-100">
          {character?.data.name ?? '加载中...'}
        </h1>
        <div className="text-xs text-text-secondary px-2">
          {affinity} · {levelLabel}
        </div>
        <button
          type="button"
          onClick={() => setShowDebug(!showDebug)}
          className="text-xs text-accent-500 px-2 py-1 rounded
                     hover:bg-surface-200 dark:hover:bg-surface-700
                     transition-colors"
        >
          RAW
        </button>
        <Button variant="ghost" size="sm" onClick={() => setShowSaves(true)}>
          存档
        </Button>
      </header>

      <div className="flex-1 overflow-y-auto p-4">
        {showDebug && (
          <div className="border-t border-surface-200 dark:border-surface-700
                          bg-surface-900 max-h-96 overflow-y-auto">
            <div className="px-3 py-1 text-xs text-accent-500 font-bold
                            border-b border-surface-700 sticky top-0 bg-surface-900">
              ▶ API 输入（发给模型的 messages 数组）
            </div>
            <pre className="text-xs p-3 whitespace-pre-wrap break-words
                            text-surface-100 max-h-48 overflow-y-auto">
              {lastApiMessages ?? '(空 - 请先发一条消息)'}
            </pre>
            <div className="px-3 py-1 text-xs text-accent-500 font-bold
                            border-b border-t border-surface-700 sticky top-0 bg-surface-900">
              ▶ AI 原始输出（fullContent）
            </div>
            <pre className="text-xs p-3 whitespace-pre-wrap break-words
                            text-surface-100 max-h-48 overflow-y-auto">
              {lastRawContent ?? '(空 - 请先发一条消息)'}
            </pre>
          </div>
        )}
        {messages
          .filter((m) => m.role !== 'system')
          .map((m) => (
            <MessageBubble
              key={m.id}
              content={m.content}
              isUser={m.role === 'user'}
              isStreaming={m.isStreaming}
              timestamp={m.timestamp}
            />
          ))}
        <div ref={messagesEndRef} />
      </div>

      <ChatInput
        onSend={handleSend}
        onStop={handleStop}
        isGenerating={isSending}
      />

      <Modal
        isOpen={showSaves}
        onClose={() => setShowSaves(false)}
        title="存档管理"
      >
        <SaveManager characterId={characterId ?? ''} />
      </Modal>
    </div>
  );
}
