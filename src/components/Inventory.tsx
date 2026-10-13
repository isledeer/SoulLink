import React from 'react';
import type { InventoryItem } from '../types/game';
import type { Item } from '../systems/InventorySystem';

interface InventoryProps {
  inventory: InventoryItem[];
  itemCatalog: Item[];
  onUseItem?: (itemId: string) => void;
}

const CELL_CLASSES =
  'aspect-square bg-surface-100 dark:bg-surface-800 rounded border border-surface-200 dark:border-surface-700 flex flex-col items-center justify-center cursor-pointer hover:bg-surface-200 dark:hover:bg-surface-700 relative text-text-primary dark:text-surface-100';

export const Inventory: React.FC<InventoryProps> = ({
  inventory,
  itemCatalog,
  onUseItem,
}) => {
  return (
    <div className="grid grid-cols-4 gap-2 p-2">
      {inventory.map((entry) => {
        const def = itemCatalog.find((c) => c.id === entry.id);

        return (
          <div
            key={entry.id}
            onClick={() => onUseItem?.(entry.id)}
            className={CELL_CLASSES}
          >
            {def ? (
              <>
                <span className="text-2xl">{def.icon}</span>
                <span className="text-xs truncate max-w-full px-1">{def.name}</span>
              </>
            ) : (
              // catalog 未注册该 id：给可见占位符，不要静默消失
              <span className="text-2xl text-text-secondary" title="未注册的物品定义">
                ?
              </span>
            )}
            {entry.quantity > 1 && (
              <span className="absolute top-0 right-0 bg-black text-white text-xs px-1 rounded-bl">
                {entry.quantity}
              </span>
            )}
          </div>
        );
      })}
    </div>
  );
};
