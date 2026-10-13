import React from 'react';

interface CharacterInfoPanelProps {
  name: string;
  description?: string;
  personality?: string;
  scenario?: string;
  firstMes?: string;
  mesExample?: string;
  avatar?: string;
}

const SECTION_TITLE = 'text-sm font-semibold mb-1';
const SECTION_BODY = 'text-xs text-text-secondary leading-relaxed whitespace-pre-wrap break-words';

export const CharacterInfoPanel: React.FC<CharacterInfoPanelProps> = ({
  name,
  description,
  personality,
  scenario,
  firstMes,
  mesExample,
  avatar,
}) => {
  const tags = personality
    ? personality.split(/[,，\n]+/).filter(Boolean)
    : [];

  return (
    <div className="space-y-6 p-4 overflow-y-auto h-full text-text-primary dark:text-surface-100">
      <div className="flex items-center gap-3">
        {avatar && (
          <img
            src={avatar}
            alt={name}
            className="w-12 h-12 rounded-full object-cover shrink-0"
          />
        )}
        <h2 className="text-lg font-bold">{name}</h2>
      </div>

      {tags.length > 0 && (
        <div className="flex flex-wrap gap-2">
          {tags.map((tag, i) => (
            <span
              key={`${tag}-${i}`}
              className="px-2 py-1 text-xs rounded-full bg-surface-200 text-text-secondary dark:bg-surface-700"
            >
              {tag.trim()}
            </span>
          ))}
        </div>
      )}

      {description && (
        <section>
          <h3 className={SECTION_TITLE}>角色描述</h3>
          <p className={SECTION_BODY}>{description}</p>
        </section>
      )}

      {scenario && (
        <section>
          <h3 className={SECTION_TITLE}>世界观设定</h3>
          <p className={SECTION_BODY}>{scenario}</p>
        </section>
      )}

      {firstMes && (
        <section>
          <h3 className={SECTION_TITLE}>开场白</h3>
          <p className={`${SECTION_BODY} italic`}>{firstMes}</p>
        </section>
      )}

      {mesExample && (
        <section>
          <h3 className={SECTION_TITLE}>对话示例</h3>
          <p className={SECTION_BODY}>{mesExample}</p>
        </section>
      )}
    </div>
  );
};
