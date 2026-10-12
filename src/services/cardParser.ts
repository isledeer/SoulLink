import type { TavernCard, CharacterMetadata } from '../types/tavern';

export class CardParseError extends Error {
  constructor(message: string, public readonly cause?: unknown) {
    super(message);
    this.name = 'CardParseError';
  }
}

const PNG_SIGNATURE = [137, 80, 78, 71, 13, 10, 26, 10];
const TEXT_CHUNK_TYPE = 'tEXt';
const CHARA_KEYWORD = 'chara';

const REQUIRED_FIELDS = [
  'name',
  'description',
  'personality',
  'scenario',
  'first_mes',
  'mes_example',
] as const;

function validateCharacterData(data: Record<string, unknown>): CharacterMetadata {
  for (const field of REQUIRED_FIELDS) {
    if (!(field in data)) {
      throw new CardParseError(`Missing required field: ${field}`);
    }
  }

  return {
    name: String(data.name),
    description: String(data.description || ''),
    personality: String(data.personality || ''),
    scenario: String(data.scenario || ''),
    first_mes: String(data.first_mes || ''),
    mes_example: String(data.mes_example || ''),
    creator_notes: data.creator_notes ? String(data.creator_notes) : undefined,
    system_prompt: data.system_prompt ? String(data.system_prompt) : undefined,
    post_history_instructions: data.post_history_instructions
      ? String(data.post_history_instructions)
      : undefined,
    tags: Array.isArray(data.tags) ? data.tags.map(String) : [],
    creator: data.creator ? String(data.creator) : undefined,
    character_version: data.character_version
      ? String(data.character_version)
      : undefined,
    extensions: (data.extensions as Record<string, unknown>) || {},
  };
}

export async function parseTavernCardFromFile(file: File): Promise<TavernCard> {
  if (!file.name.toLowerCase().endsWith('.png')) {
    throw new CardParseError('Only PNG format is supported');
  }

  try {
    const buffer = await file.arrayBuffer();
    const bytes = new Uint8Array(buffer);

    // 校验 PNG 签名
    for (let i = 0; i < PNG_SIGNATURE.length; i++) {
      if (bytes[i] !== PNG_SIGNATURE[i]) {
        throw new CardParseError('Invalid PNG file signature');
      }
    }

    // 从签名之后开始遍历 chunk
    let offset = 8;
    while (offset + 8 <= bytes.length) {
      const length =
        ((bytes[offset] << 24) |
          (bytes[offset + 1] << 16) |
          (bytes[offset + 2] << 8) |
          bytes[offset + 3]) >>>
        0;

      const type = String.fromCharCode(
        bytes[offset + 4],
        bytes[offset + 5],
        bytes[offset + 6],
        bytes[offset + 7]
      );

      if (type === TEXT_CHUNK_TYPE) {
        const dataStart = offset + 8;
        const nullIndex = bytes.indexOf(0, dataStart);

        if (nullIndex !== -1 && nullIndex < dataStart + length) {
          const keyword = new TextDecoder().decode(bytes.slice(dataStart, nullIndex));

          if (keyword === CHARA_KEYWORD) {
            const base64Data = new TextDecoder().decode(
              bytes.slice(nullIndex + 1, dataStart + length)
            );

            // atob 返回的是 Latin-1 字符串，卡数据实际是 UTF-8 字节，
            // 必须先还原成字节再按 UTF-8 解码，否则中文/emoji 会乱码
            // 注意：变量名不能叫 bytes —— 本块上方的 base64Data 还要读外层 bytes，
            // 同名 const 会让整块进入 TDZ，直接 ReferenceError
            const binary = atob(base64Data);
            const utf8Bytes = Uint8Array.from(binary, (c) => c.charCodeAt(0));
            const jsonStr = new TextDecoder('utf-8').decode(utf8Bytes);

            const parsed = JSON.parse(jsonStr) as {
              spec?: string;
              spec_version?: string;
              data?: Record<string, unknown>;
            };

            if (!parsed.data) {
              throw new CardParseError('Card missing data field');
            }

            const validated = validateCharacterData(parsed.data);

            return {
              spec: parsed.spec || 'chara_card_v2',
              spec_version: parsed.spec_version || '2.0',
              data: validated,
            };
          }
        }
      }

      // chunk = 4 字节长度 + 4 字节类型 + length 字节数据 + 4 字节 CRC
      offset += 12 + length;
    }

    throw new CardParseError('No tavern card metadata found in PNG');
  } catch (error) {
    if (error instanceof CardParseError) throw error;
    throw new CardParseError('Failed to read file', error);
  }
}
