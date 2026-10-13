import type { InventoryItem } from '../types/game';

/**
 * UI 层的完整物品定义（含 type / icon / effect）。
 * 与 InventoryItem 通过 **id + type** 一一对应：Item 是定义（展示与效果），
 * InventoryItem 是库存实例（数量）。因为字段已对齐，加进库存时可直接转换：
 *   addItem(inventory, { id, name, description, type })
 */
export interface Item {
  id: string;
  name: string;
  description: string;
  type: InventoryItem['type'];
  icon: string; // emoji 或图标 class
  effect?: {
    type: 'affection' | 'unlock_scene';
    value: number | string;
  };
}

const DEFAULT_QTY = 1;

/** 添加物品：已存在则数量相加，不存在则追加。不修改入参，返回新数组。 */
export function addItem(
  inventory: InventoryItem[],
  item: Omit<InventoryItem, 'quantity'>,
  qty: number = DEFAULT_QTY
): InventoryItem[] {
  const index = inventory.findIndex((entry) => entry.id === item.id);

  if (index === -1) {
    return [...inventory, { ...item, quantity: qty }];
  }

  return inventory.map((entry, i) =>
    i === index ? { ...entry, quantity: entry.quantity + qty } : entry
  );
}

/**
 * 移除物品。
 * 找不到或数量不足时**原样返回入参数组本身**（引用相等），
 * 调用方可用 `result === inventory` 判断是否发生变化。
 */
export function removeItem(
  inventory: InventoryItem[],
  itemId: string,
  qty: number = DEFAULT_QTY
): InventoryItem[] {
  const index = inventory.findIndex((entry) => entry.id === itemId);

  if (index === -1) return inventory;

  const entry = inventory[index];
  if (entry.quantity < qty) return inventory;

  // 数量刚好用尽 → 整条移除
  if (entry.quantity === qty) {
    return inventory.filter((item) => item.id !== itemId);
  }

  return inventory.map((item, i) =>
    i === index ? { ...item, quantity: item.quantity - qty } : item
  );
}
