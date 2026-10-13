import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useSettingsStore } from '../stores/useSettingsStore';
import { Input } from '../components/ui/Input';
import { Button } from '../components/ui/Button';

type TestResult = 'success' | 'error' | null;

export default function Settings() {
  const navigate = useNavigate();

  // 与下面 useState 的本地 setter 同名会撞名，故 store setter 统一加 storeSet 前缀
  const storeSetApiBaseUrl = useSettingsStore((s) => s.setApiBaseUrl);
  const storeSetApiKey = useSettingsStore((s) => s.setApiKey);
  const storeSetModelName = useSettingsStore((s) => s.setModelName);

  // 进入页面时用 store 现值初始化本地 state
  const [baseUrl, setBaseUrl] = useState(useSettingsStore.getState().apiBaseUrl);
  const [apiKey, setApiKey] = useState(useSettingsStore.getState().apiKey);
  const [modelName, setModelName] = useState(useSettingsStore.getState().modelName);

  const [isTesting, setIsTesting] = useState(false);
  const [testResult, setTestResult] = useState<TestResult>(null);

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
    // setter 内部已由 zustand persist 自动落盘，这里只是给用户一个显式反馈
    alert('设置已保存');
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
          <Input
            value={modelName}
            onChange={(e) => setModelName(e.target.value)}
            placeholder="gpt-3.5-turbo"
          />
        </div>

        <div className="flex gap-2">
          <Button onClick={handleTest} disabled={isTesting}>
            {isTesting ? '测试中...' : '测试连接'}
          </Button>
          <Button variant="secondary" onClick={handleSave}>
            保存
          </Button>
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
