export interface Scene {
  id: string;
  name: string;
  backgroundUrl?: string;
  ambientSound?: string;
  dialogueModifiers?: Record<string, unknown>;
}

export const SCENES: Scene[] = [
  { id: 'default', name: '默认场景' },
  { id: 'cafe', name: '街角咖啡馆', backgroundUrl: '/assets/bg/cafe.jpg' },
  { id: 'park', name: '中央公园', backgroundUrl: '/assets/bg/park.jpg' },
];

/** 按 id 查场景，找不到返回 undefined */
export function getSceneById(id: string): Scene | undefined {
  return SCENES.find((scene) => scene.id === id);
}
