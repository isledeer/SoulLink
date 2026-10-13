import { create } from 'zustand';
import { apiClient } from '../services/apiClient';
import { useSettingsStore } from './useSettingsStore';
import { generateId } from '../utils/helpers';
import type { Message } from '../types/message';

interface ChatState {
  messages: Message[];
  isSending: boolean;
  streamingMessageId: string | null;
  sendMessage: (content: string) => Promise<void>;
  appendStreamChunk: (chunk: string) => void;
  finalizeStream: (messageId: string) => void;
  failStream: (messageId: string, error: string) => void;
  clearChat: () => void;
  loadMessages: (messages: Message[]) => void;
  abortController: AbortController | null;
  abort: () => void;
}

export const useChatStore = create<ChatState>()((set, get) => ({
  messages: [],
  isSending: false,
  streamingMessageId: null,
  abortController: null,

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
      await apiClient.sendMessage(
        get().messages,
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
    });
  },

  loadMessages: (messages: Message[]) => {
    set({ messages, isSending: false, streamingMessageId: null });
  },
}));
