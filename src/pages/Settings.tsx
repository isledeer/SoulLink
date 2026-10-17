import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useSettingsStore } from '../stores/useSettingsStore';
import { Input } from '../components/ui/Input';
import { Button } from '../components/ui/Button';
import type { ReasoningEffort } from '../types/api';

type TestResult = 'success' | 'error' | null;

/** 思考强度档位；value 为 undefined 表示关闭 */
const REASONING_OPTIONS: { label: string; value: ReasoningEffort | undefined }[] = [
  { label: '关闭', value: undefined },
  { label: '低', value: 'low' },
  { label: '中', value: 'medium' },
  { label: '高', value: 'high' },
];

export default function Settings() {
  const navigate = useNavigate();

  // 与下面 useState 的本地 setter 同名会撞名，故 store setter 统一加 storeSet 前缀
  const storeSetApiBaseUrl = useSettingsStore((s) => s.setApiBaseUrl);
  const storeSetApiKey = useSettingsStore((s) => s.setApiKey);
  const storeSetModelName = useSettingsStore((s) => s.setModelName);
  const storeSetReasoningEffort = useSettingsStore((s) => s.setStoreReasoningEffort);
  const reasoningEffort = useSettingsStore((s) => s.reasoningEffort);
  const availableModels = useSettingsStore((s) => s.availableModels);
  const fetchModels = useSettingsStore((s) => s.fetchModels);

  // 进入页面时用 store 现值初始化本地 state
  const [baseUrl, setBaseUrl] = useState(useSettingsStore.getState().apiBaseUrl);
  const [apiKey, setApiKey] = useState(useSettingsStore.getState().apiKey);
  const [modelName, setModelName] = useState(useSettingsStore.getState().modelName);

  const [isTesting, setIsTesting] = useState(false);
  const [testResult, setTestResult] = useState<TestResult>(null);
  const [savedHint, setSavedHint] = useState(false);
  const [isFetchingModels, setIsFetchingModels] = useState(false);
  /** 拉取模型的结果提示：成功显示数量，失败显示错误——不用 alert */
  const [fetchResult, setFetchResult] = useState<
    { kind: 'success'; count: number } | { kind: 'error' } | null
  >(null);

  // 先把本地填写同步进 store，保证「填了没保存就点测试」测的是刚填的值
  const syncToStore = () => {
    storeSetApiBaseUrl(baseUrl);
    storeSetApiKey(apiKey);
    storeSetModelName(modelName);
  };

  const handleTest = async () => {
    syncToStore();
    setIsTesting(true);
    setTestResult(null);

    const ok = await useSettingsStore.getState().testConnection();
    setTestResult(ok ? 'success' : 'error');
    setIsTesting(false);
  };

  const handleSave = () => {
    syncToStore();
    // setter 内部已由 zustand persist 自动落盘，这里只是给用户一个显式反馈。
    // 不用 alert：踩坑 #15，弹窗会打断输入流，改用行内提示。
    setSavedHint(true);
    window.setTimeout(() => setSavedHint(false), 2000);
  };

  // 拉取模型：先把本地输入同步进 store（fetchModels 读的是 store 里的 baseUrl/key），
  // 再调用 store action。失败必须落到 UI 提示上（行内红字），不能静默。
  const handleFetchModels = async () => {
    syncToStore();
    setIsFetchingModels(true);
    setFetchResult(null);

    try {
      await useSettingsStore.getState().fetchModels();
      const count = useSettingsStore.getState().availableModels.length;
      setFetchResult({ kind: 'success', count });
    } catch {
      setFetchResult({ kind: 'error' });
    } finally {
      setIsFetchingModels(false);
    }
  };

  return (
    <div className="min-h-screen bg-surface-100 dark:bg-surface-900 text-text-primary dark:text-surface-100 p-4">
      <header className="flex items-center gap-2 mb-4">
        <Button variant="ghost" size="sm" onClick={() => navigate('/')}>
          ← 返回
        </Button>
        <h1 className="text-lg font-bold">设置</h1>
      </header>

      <div className="space-y-4">
        <div>
          <label className="text-sm font-medium mb-1 block">API Base URL</label>
          <Input
            value={baseUrl}
            onChange={(e) => setBaseUrl(e.target.value)}
            placeholder="https://api.openai.com/v1"
            type="url"
          />
        </div>

        <div>
          <label className="text-sm font-medium mb-1 block">API Key</label>
          <Input
            value={apiKey}
            onChange={(e) => setApiKey(e.target.value)}
            placeholder="sk-..."
            type="password"
          />
        </div>

        <div>
          <label className="text-sm font-medium mb-1 block">Model Name</label>
          <div className="flex gap-2">
            {/* Input 组件自带 w-full，这里用 flex-1 包一层，避免 flex 布局里挤掉右侧按钮 */}
            <div className="flex-1 min-w-0">
              <Input
                value={modelName}
                onChange={(e) => setModelName(e.target.value)}
                placeholder="gpt-3.5-turbo"
                list="model-list"
              />
            </div>
            <Button
              variant="secondary"
              onClick={handleFetchModels}
              disabled={isFetchingModels}
            >
              {isFetchingModels ? '拉取中...' : '拉取模型'}
            </Button>
          </div>
          <datalist id="model-list">
            {availableModels.map((m) => (
              <option key={m} value={m} />
            ))}
          </datalist>
          {fetchResult?.kind === 'success' && (
            <p className="text-xs text-green-600 dark:text-green-400 mt-1">
              拉取成功，共 {fetchResult.count} 个模型
            </p>
          )}
          {fetchResult?.kind === 'error' && (
            <p className="text-xs text-red-600 dark:text-red-400 mt-1">
              拉取失败，请检查 API 地址与 Key 后重试
            </p>
          )}
        </div>

        <div>
          <label className="text-sm font-medium mb-1 block">思考强度</label>
          <div className="flex gap-2">
            {REASONING_OPTIONS.map((opt) => {
              const active = reasoningEffort === opt.value;
              return (
                <button
                  key={opt.label}
                  type="button"
                  onClick={() => storeSetReasoningEffort(opt.value)}
                  className={[
                    'flex-1 rounded border px-3 py-2 text-sm font-medium transition-all active:scale-95',
                    active
                      ? 'bg-primary-600 text-white border-primary-600'
                      : 'bg-surface-0 text-text-primary border-surface-300 hover:bg-surface-200 dark:bg-surface-800 dark:text-surface-100 dark:border-surface-700 dark:hover:bg-surface-700',
                  ].join(' ')}
                >
                  {opt.label}
                </button>
              );
            })}
          </div>
          <p className="text-xs text-text-secondary dark:text-surface-300 mt-1">
            仅对支持思考的模型生效（如 o1/o3、Claude、Gemini）；关闭则不发送任何思考参数。
          </p>
        </div>

        <div className="flex gap-2">
          <Button onClick={handleTest} disabled={isTesting}>
            {isTesting ? '测试中...' : '测试连接'}
          </Button>
          <Button variant="secondary" onClick={handleSave}>
            保存
          </Button>
          {savedHint && (
            <span className="self-center text-sm text-text-secondary dark:text-surface-300">
              设置已保存
            </span>
          )}
        </div>

        {testResult === 'success' && (
          <div className="bg-green-50 text-green-700 dark:bg-green-900/30 dark:text-green-300 p-3 rounded-lg text-sm">
            连接成功
          </div>
        )}
        {testResult === 'error' && (
          <div className="bg-red-50 text-red-700 dark:bg-red-900/30 dark:text-red-300 p-3 rounded-lg text-sm">
            连接失败，请检查 API 地址与 Key
          </div>
        )}
      </div>
    </div>
  );
}
