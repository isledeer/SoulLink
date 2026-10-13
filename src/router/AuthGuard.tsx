import React from 'react';
import { Navigate } from 'react-router-dom';
import { useSettingsStore } from '../stores/useSettingsStore';

interface AuthGuardProps {
  children: React.ReactNode;
}

/**
 * 已配置 API 才能进入受保护路由。
 * 不做 useEffect + useState 的异步判断：zustand selector 本身订阅变化，
 * 用户在设置页填好配置后这里会自动重渲染。
 */
export const AuthGuard: React.FC<AuthGuardProps> = ({ children }) => {
  const apiBaseUrl = useSettingsStore((s) => s.apiBaseUrl);
  const apiKey = useSettingsStore((s) => s.apiKey);

  const isConfigured = Boolean(apiBaseUrl && apiKey);
  if (!isConfigured) return <Navigate to="/settings" replace />;

  return <>{children}</>;
};
