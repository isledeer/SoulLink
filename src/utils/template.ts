/**
 * 酒馆卡模板占位符替换。
 *
 * {{char}} → 角色名
 * {{user}} → 玩家名（缺省「你」）
 *
 * ⚠️ 刻意不用 String.prototype.replaceAll：
 *   1) tsconfig 的 lib 是 ES2020，replaceAll 属于 ES2021，真 tsc 会直接报
 *      「Property 'replaceAll' does not exist on type 'string'」；
 *   2) 更关键的是 vite build target 是 chrome79，而 replaceAll 需要 Chrome 85+，
 *      打包进 APK 后在旧版 Android WebView 上会运行时崩溃。
 *   split/join 语义与 replaceAll 完全一致（同样大小写敏感、同样替换全部出现），
 *   且 ES5 起就存在，两条限制同时满足。
 *
 * 大小写敏感：{{Char}} / {{CHAR}} 不会被替换，与酒馆卡规范保持一致。
 */
export function replacePlaceholders(
  content: string,
  charName: string,
  userName?: string
): string {
  const user = userName || '你';

  return content.split('{{char}}').join(charName).split('{{user}}').join(user);
}
