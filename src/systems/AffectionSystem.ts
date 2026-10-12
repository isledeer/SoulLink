export interface AffectionHistoryEntry {
  timestamp: number;
  change: number;
  reason: string;
}

export interface AffectionState {
  value: number; // 0..100
  level: 'stranger' | 'friend' | 'close' | 'lover';
  history: AffectionHistoryEntry[];
}

/** 单次变化上限（正负对称） */
const MAX_CHANGE = 10;
/** history 只保留最近 N 条 */
const MAX_HISTORY = 50;
/** 每命中一个关键词的变化量 */
const CHANGE_PER_KEYWORD = 5;

const POSITIVE_KEYWORDS = ['喜欢', '爱', '开心', '谢谢', '拥抱'];
const NEGATIVE_KEYWORDS = ['讨厌', '滚', '恨', '笨蛋'];

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}

/** 好感度数值 → 关系等级 */
export function getAffectionLevel(value: number): AffectionState['level'] {
  if (value >= 80) return 'lover';
  if (value >= 50) return 'close';
  if (value >= 20) return 'friend';
  return 'stranger';
}

/**
 * 简单关键词示例实现：统计 aiMessage 命中的正/负面词，
 * 每个不同关键词计一次 ±5，最终 clamp 到 [-10, 10]。
 * userMessage 目前不参与计算，保留形参供后续接入更复杂的判定。
 */
export function calculateAffectionChange(
  userMessage: string,
  aiMessage: string
): number {
  void userMessage;

  let change = 0;

  for (const keyword of POSITIVE_KEYWORDS) {
    if (aiMessage.includes(keyword)) change += CHANGE_PER_KEYWORD;
  }

  for (const keyword of NEGATIVE_KEYWORDS) {
    if (aiMessage.includes(keyword)) change -= CHANGE_PER_KEYWORD;
  }

  return clamp(change, -MAX_CHANGE, MAX_CHANGE);
}

/** 应用一次好感度变化：value clamp 到 [0,100]，history 追加并只留最近 50 条，重算 level */
export function updateAffection(
  current: AffectionState,
  change: number,
  reason: string
): AffectionState {
  const value = clamp(current.value + change, 0, 100);

  const history = [
    ...current.history,
    { timestamp: Date.now(), change, reason },
  ].slice(-MAX_HISTORY);

  return {
    value,
    level: getAffectionLevel(value),
    history,
  };
}
