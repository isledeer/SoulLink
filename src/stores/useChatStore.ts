import { create } from 'zustand';
import { apiClient } from '../services/apiClient';
import { useSettingsStore } from './useSettingsStore';
import { useCharacterStore } from './useCharacterStore';
import { useSaveStore } from './useSaveStore';
import {
  parseGameBlock,
  applyGameAction,
  buildSystemPrompt,
} from '../systems/GameEngine';
import { replacePlaceholders } from '../utils/template';
import { generateId } from '../utils/helpers';
import type { Message } from '../types/message';

interface ChatState {
  messages: Message[];
  isSending: boolean;
  streamingMessageId: string | null;
  /**
   * 当前对话绑定的角色 id。
   * 由 Chat 页挂载时写入、卸载时清空；sendMessage 靠它决定注入哪个角色的设定。
   * 刻意不持久化：这是「当前页面上下文」，不是用户配置。
   */
  currentCharacterId: string | null;
  /**
   * 最后一次 AI 回复的**原始** fullContent（含 <game> 块，未经解析/剥离）。
   * 只给 debug 视图用：UI 显示永远走 messages 里的 cleanContent。
   */
  lastRawContent: string | null;
  setCurrentCharacterId: (id: string | null) => void;
  sendMessage: (content: string) => Promise<void>;
  appendStreamChunk: (chunk: string) => void;
  finalizeStream: (messageId: string) => void;
  failStream: (messageId: string, error: string) => void;
  clearChat: () => void;
  loadMessages: (messages: Message[]) => void;
  abortController: AbortController | null;
  abort: () => void;
}

/** 注入给模型的 system 消息用固定 id，便于调试时一眼认出它不是真实对话 */
const SYSTEM_MESSAGE_ID = 'system-game';

/**
 * 构造真正发给 API 的消息数组。
 *
 * 与 store 里的 messages **完全解耦**（UI 继续显示原始 {{char}}），
 * 返回的是全新数组与新对象，不改动任何 store 状态。
 *
 * 降级策略：拿不到角色或当前存档时，直接用原始 messages，不做任何注入。
 */
function buildApiMessages(
  messages: Message[],
  currentCharacterId: string | null
): Message[] {
  // 流式占位消息（content 为空）绝不能发给 API，空 assistant 消息会被部分服务端拒绝
  const usable = messages.filter((m) => m.content.trim().length > 0);

  if (!currentCharacterId) return usable;

  const character = useCharacterStore.getState().getCharacterById(currentCharacterId);
  if (!character) return usable;

  const saveState = useSaveStore.getState();
  const currentSave = saveState.saves.find((s) => s.id === saveState.currentSaveId);
  if (!currentSave) return usable;

  const systemMessage: Message = {
    id: SYSTEM_MESSAGE_ID,
    role: 'system',
    content: buildSystemPrompt(character, currentSave.gameState),
    timestamp: Date.now(),
  };

  const rest = usable.map((m) => ({
    ...m,
    content: replacePlaceholders(m.content, character.data.name, '你'),
  }));

  return [systemMessage, ...rest];
}

export const useChatStore = create<ChatState>()((set, get) => ({
  messages: [],
  isSending: false,
  streamingMessageId: null,
  currentCharacterId: null,
  lastRawContent: null,
  abortController: null,

  setCurrentCharacterId: (id: string | null) => {
    set({ currentCharacterId: id });
  },

  sendMessage: async (content: string) => {
    const { apiBaseUrl, apiKey, modelName, compatibilityMode } =
      useSettingsStore.getState();

    // 未配置接口时给出可见提示，而不是静默失败
    if (!apiBaseUrl || !apiKey) {
      const notice: Message = {
        id: generateId(),
        role: 'assistant',
        content: '尚未配置 API 地址或 API Key，请先前往设置页完成配置。',
        timestamp: Date.now(),
      };
      set((state) => ({ messages: [...state.messages, notice] }));
      return;
    }

    const userMsg: Message = {
      id: generateId(),
      role: 'user',
      content,
      timestamp: Date.now(),
    };

    set((state) => ({
      messages: [...state.messages, userMsg],
      isSending: true,
    }));

    const aiMsgId = generateId();
    const aiPlaceholder: Message = {
      id: aiMsgId,
      role: 'assistant',
      content: '',
      timestamp: Date.now(),
      isStreaming: true,
    };

    set((state) => ({
      messages: [...state.messages, aiPlaceholder],
      streamingMessageId: aiMsgId,
    }));

    const controller = new AbortController();
    set({ abortController: controller });

    try {
      const apiMessages = buildApiMessages(get().messages, get().currentCharacterId);

      const fullContent = await apiClient.sendMessage(
        apiMessages,
        {
          baseUrl: apiBaseUrl,
          apiKey,
          model: modelName,
          maxTokens: 1024,
          temperature: 0.8,
          compatibleMode: compatibilityMode,
        },
        (chunk) => get().appendStreamChunk(chunk),
        controller.signal
      );

      // ---- 游戏引擎收口：解析 → 改消息 → 落库 gameState ----
      // 解析只在这里做：finalizeStream 保持「只改状态」的单一职责。

      // debug 用：留一份解析前的原文（含 <game> 块），UI 显示仍用 cleanContent
      set({ lastRawContent: fullContent });

      const { cleanContent, gameAction } = parseGameBlock(fullContent);

      set((state) => ({
        messages: state.messages.map((m) =>
          m.id === aiMsgId ? { ...m, content: cleanContent } : m
        ),
      }));

      if (gameAction) {
        const saveState = useSaveStore.getState();
        const currentSave = saveState.saves.find(
          (s) => s.id === saveState.currentSaveId
        );

        if (currentSave) {
          const newGameState = applyGameAction(currentSave.gameState, gameAction);
          // 落库失败不能连累对话：消息已经生成完了，这里只记录错误
          try {
            await saveState.updateGameState(currentSave.id, newGameState);
          } catch (error) {
            console.error('[GameEngine] 游戏状态保存失败', error);
          }
        }
      }

      get().finalizeStream(aiMsgId);
    } catch (error) {
      // 用户主动中断时保留已生成内容，只有真正的失败才替换成错误文案
      if ((error as Error).name === 'AbortError') {
        get().finalizeStream(aiMsgId);
      } else {
        get().failStream(aiMsgId, '抱歉，生成内容时发生错误，请稍后重试。');
      }
    } finally {
      set({ abortController: null });
    }
  },

  abort: () => {
    get().abortController?.abort();
  },

  appendStreamChunk: (chunk: string) => {
    set((state) => {
      const { streamingMessageId } = state;
      if (!streamingMessageId) return state;

      const index = state.messages.findIndex((m) => m.id === streamingMessageId);
      if (index === -1) return state;

      const messages = state.messages.slice();
      messages[index] = {
        ...messages[index],
        content: messages[index].content + chunk,
      };

      return { messages };
    });
  },

  finalizeStream: (messageId: string) => {
    set((state) => ({
      messages: state.messages.map((m) =>
        m.id === messageId ? { ...m, isStreaming: false } : m
      ),
      isSending: false,
      streamingMessageId: null,
    }));
  },

  failStream: (messageId: string, error: string) => {
    set((state) => ({
      messages: state.messages.map((m) =>
        m.id === messageId ? { ...m, content: error, isStreaming: false } : m
      ),
      isSending: false,
      streamingMessageId: null,
    }));
  },

  clearChat: () => {
    set({
      messages: [],
      isSending: false,
      streamingMessageId: null,
      abortController: null,
      lastRawContent: null,
    });
  },

  loadMessages: (messages: Message[]) => {
    set({ messages, isSending: false, streamingMessageId: null });
  },
}));
