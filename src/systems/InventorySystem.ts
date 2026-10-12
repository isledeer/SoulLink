export interface Item {
  id: string;
  name: string;
  description: string;
  icon: string; // emoji 或图标 class
  effect?: {
    type: 'affection' | 'unlock_scene';
    value: number | string;
  };
}

export interface InventoryState {
  items: { itemId: string; quantity: number }[];
}

const DEFAULT_QTY = 1;

/** 添加物品：已存在则数量相加，不存在则新增条目。不修改入参。 */
export function addItem(
  inventory: InventoryState,
  itemId: string,
  qty: number = DEFAULT_QTY
): InventoryState {
  const index = inventory.items.findIndex((entry) => entry.itemId === itemId);

  if (index === -1) {
    return {
      items: [...inventory.items, { itemId, quantity: qty }],
    };
  }

  return {
    items: inventory.items.map((entry, i) =>
      i === index ? { ...entry, quantity: entry.quantity + qty } : entry
    ),
  };
}

/**
 * 移除物品。
 * 不存在或数量不足时**原样返回入参本身**（引用相等），调用方可用 `result === inventory` 判断是否发生变化。
 */
export function removeItem(
  inventory: InventoryState,
  itemId: string,
  qty: number = DEFAULT_QTY
): InventoryState {
  const index = inventory.items.findIndex((entry) => entry.itemId === itemId);

  if (index === -1) return inventory;

  const entry = inventory.items[index];
  if (entry.quantity < qty) return inventory;

  // 数量刚好用尽 → 整条移除
  if (entry.quantity === qty) {
    return {
      items: inventory.items.filter((item) => item.itemId !== itemId),
    };
  }

  return {
    items: inventory.items.map((item, i) =>
      i === index ? { ...item, quantity: item.quantity - qty } : item
    ),
  };
}
