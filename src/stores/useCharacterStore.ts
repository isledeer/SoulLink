import { create } from 'zustand';
import { db } from '../services/database';
import { generateId } from '../utils/helpers';
import type { TavernCard } from '../types/tavern';

/**
 * 角色记录 = 酒馆卡 + 本地主键（+ 可选头像）。
 * ⚠️ 刻意没有 createdAt：database.ts 的 characters store 里没有存这个字段，
 *    store 层不假装有。真需要时改 database schema（单独一期）。
 */
export interface CharacterRecord extends TavernCard {
  id: string;
  avatar?: string;
}

interface CharacterState {
  characters: CharacterRecord[];
  isLoaded: boolean;
  loadCharacters: () => Promise<void>;
  addCharacter: (card: TavernCard, avatar?: string) => Promise<string>;
  getCharacterById: (id: string) => CharacterRecord | undefined;
  clear: () => void;
}

export const useCharacterStore = create<CharacterState>()((set, get) => ({
  characters: [],
  isLoaded: false,

  loadCharacters: async () => {
    const records = await db.getAllCharacters();
    // records 是 (TavernCard & { id: string })[]，不虚构任何字段
    set({ characters: records as CharacterRecord[], isLoaded: true });
  },

  addCharacter: async (card: TavernCard, avatar?: string) => {
    const id = generateId();
    await db.addCharacter({ ...card, id, avatar });

    const record: CharacterRecord = { ...card, id, avatar };
    set((state) => ({ characters: [...state.characters, record] }));

    return id;
  },

  getCharacterById: (id: string) => get().characters.find((c) => c.id === id),

  clear: () => {
    set({ characters: [], isLoaded: false });
  },
}));
