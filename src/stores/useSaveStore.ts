import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { db } from '../services/database';
import { useChatStore } from './useChatStore';
import { generateId } from '../utils/helpers';
import type { SaveFile } from '../types/save';
import type { GameState } from '../types/game';

/** 自由对话（无角色卡）存档的默认名 */
export const FREE_CHAT_SAVE_NAME = 'isledeer 的对话';

interface CreateSaveInit {
  /** null = 自由对话（内置 isledeer），不绑定任何角色卡 */
  characterId: string | null;
  name?: string;
  messages?: SaveFile['messages'];
  gameState?: Partial<GameState>;
}

interface SaveStore {
  saves: SaveFile[];
  currentSaveId: string | null;

  loadSaves: () => Promise<void>;
  createSave: (init: CreateSaveInit) => Promise<string>;
  updateSave: (id: string, patch: Partial<SaveFile>) => Promise<void>;
  updateGameState: (id: string, gameState: GameState) => Promise<void>;
  deleteSave: (id: string) => Promise<void>;
  switchSave: (id: string) => Promise<void>;
  setCurrentSave: (id: string | null) => void;
}

export const useSaveStore = create<SaveStore>()(
  persist(
    (set, get) => ({
      saves: [],
      currentSaveId: null,

      loadSaves: async () => {
        const saves = await db.getAllSaves();
        set({ saves });
      },

      createSave: async (init: CreateSaveInit) => {
        const id = generateId();
        const now = Date.now();

        // 自由对话（characterId === null）没给名字时用 isledeer 默认名兜底；
        // 带角色的存档若也漏传名字，用「未命名对话」兜底，避免存出一个空标题的档。
        const trimmedName = (init.name ?? '').trim();
        const name =
          trimmedName ||
          (init.characterId === null ? FREE_CHAT_SAVE_NAME : '未命名对话');

        const full: SaveFile = {
          id,
          name,
          characterId: init.characterId,
          messages: init.messages ?? [],
          gameState: {
            affinity: 0,
            inventory: [],
            currentScene: 'default',
            flags: {},
            ...init.gameState,
          },
          createdAt: now,
          updatedAt: now,
        };

        await db.addSave(full);

        set((state) => ({
          saves: [...state.saves, full],
          currentSaveId: id,
        }));

        return id;
      },

      updateSave: async (id: string, patch: Partial<SaveFile>) => {
        const existing = get().saves.find((s) => s.id === id);
        if (!existing) return;

        const merged: SaveFile = { ...existing, ...patch };

        // database.ts 内部会把库里的 updatedAt 刷新，这里同步内存副本
        const updatedAt = Date.now();
        await db.updateSave(merged);

        set((state) => ({
          saves: state.saves.map((s) =>
            s.id === id ? { ...merged, updatedAt } : s
          ),
        }));
      },

      /**
       * 只替换 gameState，其余字段保持原样。
       * 与 updateSave 分开，是为了给 GameEngine 一个「语义明确、不会被
       * 顺手改到 messages」的入口；内部仍复用 updateSave 的落库 + 内存同步逻辑。
       */
      updateGameState: async (id: string, gameState: GameState) => {
        await get().updateSave(id, { gameState });
      },

      deleteSave: async (id: string) => {
        await db.deleteSave(id);

        set((state) => ({
          saves: state.saves.filter((s) => s.id !== id),
          currentSaveId: state.currentSaveId === id ? null : state.currentSaveId,
        }));
      },

      switchSave: async (id: string) => {
        const save = await db.getSaveById(id);
        if (!save) return;

        useChatStore.getState().loadMessages(save.messages);
        set({ currentSaveId: id });
      },

      setCurrentSave: (id: string | null) => {
        set({ currentSaveId: id });
      },
    }),
    {
      name: 'save-meta-storage',
      // 只持久化 currentSaveId，saves 每次从 IndexedDB 拉
      partialize: (s) => ({ currentSaveId: s.currentSaveId }),
    }
  )
);
