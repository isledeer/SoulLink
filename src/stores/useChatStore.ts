import { create } from 'zustand';
import { apiClient } from '../services/apiClient';
import { useSettingsStore } from './useSettingsStore';
import { useCharacterStore } from './useCharacterStore';
import { useSaveStore } from './useSaveStore';
import {
  parseGameBlock,
  applyGameAction,
  appendGameInstruction,
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
  /**
   * 最后一次真正发给 API 的 messages 数组（JSON 美化后的字符串）。
   * debug 视图用它诊断 Bug 2：first_mes 在数组里的位置、system 注入是否干扰历史。
   */
  lastApiMessages: string | null;
  setCurrentCharacterId: (id: string | null) => void;
  sendMessage: (content: string) => Promise<void>;
  /**
   * 改写某条消息的正文，并打上 isEdited 标记。
   * 消息不存在时静默返回；正在流式生成时整体拒绝（返回 void，不抛错）。
   */
  editMessage: (id: string, newContent: string) => void;
  /**
   * 从 messages 中移除某条消息。
   * 若删的正是流式中的那条，先中断请求再删。
   * 刻意不做「删完自动重发」：messages 为空或以 user 结尾都保持现状。
   */
  deleteMessage: (id: string) => void;
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
 * 防空白回复：模型返回的纯文本为空时最多再试几次。
 * 总尝试次数 = 1 + EMPTY_REPLY_MAX_RETRIES。
 */
const EMPTY_REPLY_MAX_RETRIES = 2;

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
  // 失败产生的错误文案绝不能进 API 输入：它不是角色说过的话
  const withoutErrors = messages.filter((m) => !m.isError);

  // 流式占位消息（content 为空）绝不能发给 API，空 assistant 消息会被部分服务端拒绝
  const usable = withoutErrors.filter((m) => m.content.trim().length > 0);

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
  lastApiMessages: null,
  abortController: null,

  setCurrentCharacterId: (id: string | null) => {
    set({ currentCharacterId: id });
  },

  editMessage: (id: string, newContent: string) => {
    // 流式进行中整体拒绝：此刻改 content 会被后续 chunk 追加覆盖，不如不改。
    // 按任务卡要求静默返回，不抛错。
    if (get().streamingMessageId !== null) return;

    // 消息不存在时静默返回
    if (!get().messages.some((m) => m.id === id)) return;

    set((state) => ({
      messages: state.messages.map((m) =>
        m.id === id ? { ...m, content: newContent, isEdited: true } : m
      ),
    }));
  },

  deleteMessage: (id: string) => {
    // 删的正是流式中的那条：先中断请求。项目里没有独立的 stopStreaming，
    // 中断入口统一是 abort()（停止按钮走的就是它）。
    if (get().streamingMessageId === id) {
      get().abort();
      // 立刻清掉流式态，UI 才不会卡在「生成中」；
      // 在途的 sendMessage 随后也会走一次 finalizeStream，两次都幂等。
      get().finalizeStream(id);
    }

    // 刻意不做「删完自动重发」：删空、或以 user 结尾，都保持现状。
    set((state) => ({
      messages: state.messages.filter((m) => m.id !== id),
    }));
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

    /**
     * 单次尝试：组装请求 → 收流 → 解析 <game> → 落库 gameState。
     * 返回剥离 <game> 后的纯文本，供外层判断是否空白回复；
     * 返回 null 表示这条消息在等待期间已被删除，调用方应立刻收手。
     * 异常一律向外抛，由外层统一按 AbortError / 真失败分流。
     */
    let lastHadGameAction = false;
  const attemptOnce = async (): Promise<string | null> => {
      if (!get().messages.some((m) => m.id === aiMsgId)) return null;

      const withSystem = buildApiMessages(get().messages, get().currentCharacterId);

      // Bug 1 修复：把 game 输出指令附到最后一条 user 消息末尾。
      // 放在 system prompt 里会被角色卡的沉浸式指令压过，真机上模型不输出 <game>。
      const apiMessages = appendGameInstruction(withSystem);

      // debug 用：留一份**实际发出**的数组（已含 system 注入与 game 指令）
      set({ lastApiMessages: JSON.stringify(apiMessages, null, 2) });

      const fullContent = await apiClient.sendMessage(
        apiMessages,
        {
          baseUrl: apiBaseUrl,
          apiKey,
          model: modelName,
          maxTokens: 1024,
          temperature: 0.8,
          compatibleMode: compatibilityMode,
          reasoningEffort: useSettingsStore.getState().reasoningEffort,
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

      lastHadGameAction = !!gameAction;
    return cleanContent;
    };

    try {
      let cleanContent: string | null = null;

      /** 这条 AI 消息是否还在 messages 里。被 deleteMessage 删掉后，就不该再动全局状态了。 */
      const aiMessageAlive = () => get().messages.some((m) => m.id === aiMsgId);

      // 防空白回复：首次 + 最多 2 次重试，共 3 次机会。
      // 重试计数只活在这个局部变量里，不写进 store（避免污染持久化与 debug 视图）。
      for (let attempt = 0; attempt <= EMPTY_REPLY_MAX_RETRIES; attempt++) {
        if (attempt > 0) {
          // 清掉上一轮的残留（含流式追加的碎片），否则两次回复会拼在一起
          set((state) => ({
            messages: state.messages.map((m) =>
              m.id === aiMsgId ? { ...m, content: '' } : m
            ),
            streamingMessageId: aiMsgId,
          }));
        }

        cleanContent = await attemptOnce();

        // 消息已被删除：不再重试，也不再改状态
        if (cleanContent === null) return;

        // 有实质文本就收工；注意判的是剥离 <game> 之后的纯文本
        if (cleanContent.trim().length > 0 || lastHadGameAction) break;

        const isLastChance = attempt === EMPTY_REPLY_MAX_RETRIES;
        if (!isLastChance) {
          console.warn(
            `[Chat] 模型返回空内容，自动重试（第 ${attempt + 1}/${EMPTY_REPLY_MAX_RETRIES} 次）`
          );
        }
      }

      if (cleanContent !== null && (cleanContent.trim().length > 0 || lastHadGameAction)) {
        get().finalizeStream(aiMsgId);
      } else if (aiMessageAlive()) {
        // 重试用尽仍是空白：保留这条空白消息，但标记为失败，
        // 这样它不会污染后续 API 输入（buildApiMessages 会过滤 isError）。
        set((state) => ({
          messages: state.messages.map((m) =>
            m.id === aiMsgId ? { ...m, isStreaming: false, isError: true } : m
          ),
          isSending: false,
          streamingMessageId: null,
        }));
      }
    } catch (error) {
      // 消息已被删除（用户删掉了正在生成的那条）：整轮就此作废，
      // 不能再动 isSending / streamingMessageId，否则会误伤紧接着发起的新一轮。
      if (!get().messages.some((m) => m.id === aiMsgId)) return;

      // 用户主动中断时保留已生成内容，只有真正的失败才替换成错误文案
      if ((error as Error).name === 'AbortError') {
        get().finalizeStream(aiMsgId);
      } else {
        get().failStream(aiMsgId, '抱歉，生成内容时发生错误，请稍后重试。');
      }
    } finally {
      // 只在仍是自己那个 controller 时收口：中途被 deleteMessage 中断并重发时，
      // 不能把新一轮的 controller 误置空
      if (get().abortController === controller) set({ abortController: null });
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
        m.id === messageId
          ? { ...m, content: error, isStreaming: false, isError: true }
          : m
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
      lastApiMessages: null,
    });
  },

  loadMessages: (messages: Message[]) => {
    set({ messages, isSending: false, streamingMessageId: null });
  },
}));
