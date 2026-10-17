import type { Message } from './message';
import type { GameState } from './game';

export interface SaveFile {
  id: string;
  name: string;
  /**
   * 绑定的角色 id。
   * `null` 代表**自由对话**：不导入任何角色卡，以内置的 isledeer（极客助手）身份对话。
   */
  characterId: string | null;
  messages: Message[];
  gameState: GameState;
  createdAt: number;
  updatedAt: number;
}
