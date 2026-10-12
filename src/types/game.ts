export interface GameState {
  affinity: number;
  inventory: InventoryItem[];
  currentScene: string;
  flags: Record<string, boolean | string | number>;
}

export interface InventoryItem {
  id: string;
  name: string;
  description: string;
  quantity: number;
  type: 'consumable' | 'key_item' | 'equipment';
}
