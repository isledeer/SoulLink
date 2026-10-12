/**
 * 唯一 ID 生成入口。
 * 刻意不使用 crypto.randomUUID：WebToApp 打包后若以 file:// 协议加载，
 * crypto.randomUUID 为 undefined，调用即抛错。
 */
export function generateId(): string {
  return `${Date.now()}-${Math.random().toString(36).substring(2, 11)}`;
}

/** 时间戳 → 'YYYY-MM-DD HH:mm' */
export function formatDate(timestamp: number): string {
  const date = new Date(timestamp);
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  const hours = String(date.getHours()).padStart(2, '0');
  const minutes = String(date.getMinutes()).padStart(2, '0');
  return `${year}-${month}-${day} ${hours}:${minutes}`;
}

/** 深拷贝；空值与非对象直接返回，其余走 JSON 序列化 */
export function deepClone<T>(obj: T): T {
  if (obj === null || typeof obj !== 'object') return obj;
  return JSON.parse(JSON.stringify(obj));
}

const HTML_ENTITY_MAP: Record<string, string> = {
  '&': '&amp;',
  '<': '&lt;',
  '>': '&gt;',
  '"': '&quot;',
  "'": '&#x27;',
  '/': '&#x2F;',
};

/** 转义 HTML 敏感字符，防止消息内容被当作标签解析 */
export function sanitizeHTML(str: string): string {
  return str.replace(/[&<>"'/]/g, (char) => HTML_ENTITY_MAP[char]);
}

/** 标准 debounce：delay 内的连续调用只执行最后一次 */
export function debounce<T extends (...args: never[]) => void>(
  fn: T,
  delay: number
): (...args: Parameters<T>) => void {
  let timer: ReturnType<typeof setTimeout>;
  return (...args: Parameters<T>) => {
    clearTimeout(timer);
    timer = setTimeout(() => fn(...args), delay);
  };
}
