import { updateAffection, getAffectionLevel } from './AffectionSystem';
import { addItem, removeItem } from './InventorySystem';
import type { GameState, InventoryItem } from '../types/game';

/**
 * AI 在回复末尾输出的游戏动作块。
 * 所有字段都是可选的：模型可以只改好感度，也可以只换场景。
 */
export interface GameAction {
  affinity_delta?: number;
  reason?: string;
  items_gained?: string[];
  items_lost?: string[];
  scene?: string;
}

export const DEFAULT_GAME_ENGINE_PROMPT = `你是一个角色扮演引擎，需要在回复末尾输出游戏状态变化。

在回复的正文结束后，另起一行输出：
<game>{"affinity_delta": 0, "reason": "简短理由"}</game>

规则：
- affinity_delta: -10 到 +10 之间的整数
  根据用户行为的亲疏、语气、情感表露程度判断
  日常友好 +1~+3，情感表露 +3~+8，冒犯 -5~-15
- reason: 一句话说明为什么
- 无变化时用 0
- items_gained / items_lost / scene 是可选字段（数组 / 字符串）
- <game> 行必须放在整个回复的最后，不要用代码块包裹
- 不要在正文里提这件事，不要让用户察觉`;

/** 匹配任意 <game>...</game> 块（非贪婪，跨行） */
const GAME_BLOCK_RE = /<game>[\s\S]*?<\/game>/g;
const GAME_OPEN = '<game>';
const GAME_CLOSE = '</game>';

/**
 * 从 AI 回复中剥离 <game> 块并解析出游戏动作。
 *
 * 行为：
 * - 有块 + JSON 合法 → 提取 gameAction，cleanContent 剥离块
 * - 有块 + JSON 非法 → gameAction = null，cleanContent 依然剥离块
 * - 完全没有块      → 原样返回 content（连尾部空白都不动）
 * - 只有 <game> 没有 </game> → 视为没匹配，原样返回（宁可漏剥，也不吃掉正文）
 *
 * ⚠️ 与任务卡给的 /<game>\s*(\{[\s\S]*?\})\s*<\/game>/g 有一处实测差异：
 *   那个正则要求块内必须有成对花括号，于是匹配不到 <game>不是json</game>。
 *   可任务卡自己的用例 e 又要求这种情况「剥离块 + action 为 null」——
 *   两条规则互相矛盾，用原正则会连标签一起留在正文里（已实测复现）。
 *   本实现先按块边界切分、再对内层文本做 JSON.parse：
 *   JSON 合法性完全交给 parse，标签剥离不再依赖内容长什么样。
 */
export function parseGameBlock(content: string): {
  cleanContent: string;
  gameAction: GameAction | null;
} {
  // matchAll 需要全局正则；每次调用重置 lastIndex，避免跨调用残留
  GAME_BLOCK_RE.lastIndex = 0;
  const blocks = Array.from(content.matchAll(GAME_BLOCK_RE));

  if (blocks.length === 0) {
    return { cleanContent: content, gameAction: null };
  }

  let gameAction: GameAction | null = null;

  for (const block of blocks) {
    const inner = block[0].slice(GAME_OPEN.length, -GAME_CLOSE.length).trim();

    try {
      const parsed = JSON.parse(inner) as unknown;
      // 只接受对象，数组/数字/字符串一律视为无效
      if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
        gameAction = parsed as GameAction;
        break; // 取第一个合法块
      }
    } catch {
      // 单个块解析失败：继续试后面的块，全失败则 gameAction 保持 null
    }
  }

  const cleanContent = content.replace(GAME_BLOCK_RE, '').replace(/\s+$/, '');

  return { cleanContent, gameAction };
}

/**
 * 物品目录（catalog）落地前的临时 id 策略：直接用中文名当 id。
 *
 * ⚠️ 这是任务卡明确认可的临时方案，但必须**确定性**：
 *   items_lost 拿到的只有名字，若 id 由 generateId() 随机生成，
 *   移除时永远找不到对应条目。批次 3 接入真实 catalog 时，
 *   把这个函数换成 catalog 查表即可（addItem / removeItem 两处都走它）。
 */
function tempItemId(name: string): string {
  return name;
}

/**
 * 把一次游戏动作应用到 GameState。
 * 始终返回**新对象**，绝不修改入参。
 *
 * ⚠️ 好感度只保存数值，不持久化 AffectionState.history：
 *    GameState 里没有 history 字段，这里给 updateAffection 传一个空的
 *    临时 state 只为复用它的 clamp（[0,100]）与 level 计算。
 */
export function applyGameAction(
  gameState: GameState,
  action: GameAction
): GameState {
  const next: GameState = { ...gameState };

  // 1) 好感度
  if (typeof action.affinity_delta === 'number' && action.affinity_delta !== 0) {
    const updated = updateAffection(
      {
        value: gameState.affinity,
        level: getAffectionLevel(gameState.affinity),
        history: [],
      },
      action.affinity_delta,
      action.reason ?? ''
    );
    next.affinity = updated.value;
  }

  // 2) 获得物品
  if (Array.isArray(action.items_gained)) {
    for (const name of action.items_gained) {
      if (!name) continue;
      const item: Omit<InventoryItem, 'quantity'> = {
        id: tempItemId(name),
        name,
        description: '',
        type: 'key_item',
      };
      next.inventory = addItem(next.inventory, item, 1);
    }
  }

  // 3) 失去物品
  if (Array.isArray(action.items_lost)) {
    for (const name of action.items_lost) {
      if (!name) continue;
      next.inventory = removeItem(next.inventory, tempItemId(name), 1);
    }
  }

  // 4) 场景切换（空串视为「没给」，不改）
  if (typeof action.scene === 'string' && action.scene.length > 0) {
    next.currentScene = action.scene;
  }

  return next;
}

/** buildSystemPrompt 需要的最小角色形状（CharacterRecord 天然满足） */
export interface SystemPromptCharacter {
  data: {
    name: string;
    description?: string;
    personality?: string;
    scenario?: string;
    system_prompt?: string;
  };
}

/**
 * 拼装发给模型的 system prompt：引擎规则 + 角色设定 + 当前游戏状态。
 * 角色卡里为空的字段整段跳过，不留空标题。
 */
export function buildSystemPrompt(
  character: SystemPromptCharacter,
  gameState: GameState
): string {
  const { name, description, personality, scenario, system_prompt } =
    character.data;

  let prompt = DEFAULT_GAME_ENGINE_PROMPT;

  prompt += '\n\n【角色设定】\n';
  if (name) prompt += `名称：${name}\n`;
  if (description) prompt += `描述：${description}\n`;
  if (personality) prompt += `性格：${personality}\n`;
  if (scenario) prompt += `场景设定：${scenario}\n`;
  if (system_prompt) prompt += `${system_prompt}\n`;

  const level = getAffectionLevel(gameState.affinity);
  const inventoryText =
    gameState.inventory.map((i) => `${i.name} x${i.quantity}`).join('、') || '空';

  prompt += '\n\n【当前游戏状态】\n';
  prompt += `好感度：${gameState.affinity}（${level}）\n`;
  prompt += `背包：${inventoryText}\n`;
  prompt += `当前场景：${gameState.currentScene}`;

  return prompt;
}
