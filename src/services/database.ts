import { openDB } from 'idb';
import type { DBSchema, IDBPDatabase } from 'idb';
import type { SaveFile } from '../types/save';
import type { TavernCard } from '../types/tavern';

const DB_NAME = 'ai_rp_db';
const DB_VERSION = 1;

/** 角色卡记录：酒馆卡 + 本地主键 + 可选头像 */
export type CharacterRecord = TavernCard & { id: string; avatar?: string };

interface AppDB extends DBSchema {
  saves: {
    key: string;
    value: SaveFile;
    indexes: { 'by-character': string; 'by-updated': number };
  };
  characters: {
    key: string;
    value: CharacterRecord;
    indexes: { 'by-name': string };
  };
  config: {
    key: string;
    value: unknown;
  };
}

/** 已打开的数据库实例（lazy singleton） */
let dbInstance: IDBPDatabase<AppDB> | null = null;
/** 打开中的数据库（防止并发首调重复 openDB） */
let dbOpening: Promise<IDBPDatabase<AppDB>> | null = null;

async function getDB(): Promise<IDBPDatabase<AppDB>> {
  if (dbInstance) return dbInstance;

  if (!dbOpening) {
    dbOpening = openDB<AppDB>(DB_NAME, DB_VERSION, {
      upgrade(db) {
        if (!db.objectStoreNames.contains('saves')) {
          const saveStore = db.createObjectStore('saves', { keyPath: 'id' });
          saveStore.createIndex('by-character', 'characterId');
          saveStore.createIndex('by-updated', 'updatedAt');
        }

        if (!db.objectStoreNames.contains('characters')) {
          const charStore = db.createObjectStore('characters', { keyPath: 'id' });
          charStore.createIndex('by-name', 'data.name');
        }

        if (!db.objectStoreNames.contains('config')) {
          db.createObjectStore('config');
        }
      },
    })
      .then((database) => {
        dbInstance = database;
        return database;
      })
      .catch((error: unknown) => {
        // 打开失败时清空缓存，允许下次重试
        dbOpening = null;
        throw error;
      });
  }

  return dbOpening;
}

export const db = {
  // ---------- saves ----------

  async getAllSaves(): Promise<SaveFile[]> {
    const database = await getDB();
    return database.getAll('saves');
  },

  async getSaveById(id: string): Promise<SaveFile | undefined> {
    const database = await getDB();
    return database.get('saves', id);
  },

  async addSave(save: SaveFile): Promise<void> {
    const database = await getDB();
    await database.put('saves', save);
  },

  async updateSave(save: SaveFile): Promise<void> {
    const database = await getDB();
    const tx = database.transaction('saves', 'readwrite');
    await tx.store.put({ ...save, updatedAt: Date.now() });
    await tx.done;
  },

  async deleteSave(id: string): Promise<void> {
    const database = await getDB();
    await database.delete('saves', id);
  },

  // ---------- characters ----------

  async addCharacter(card: CharacterRecord): Promise<void> {
    const database = await getDB();
    await database.put('characters', card);
  },

  async getCharacterById(id: string): Promise<(TavernCard & { id: string }) | undefined> {
    const database = await getDB();
    return database.get('characters', id);
  },

  async getAllCharacters(): Promise<(TavernCard & { id: string })[]> {
    const database = await getDB();
    return database.getAll('characters');
  },
};
