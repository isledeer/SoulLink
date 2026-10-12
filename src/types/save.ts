import type { Message } from './message';
import type { GameState } from './game';

export interface SaveFile {
  id: string;
  name: string;
  characterId: string;
  messages: Message[];
  gameState: GameState;
  createdAt: number;
  updatedAt: number;
}
