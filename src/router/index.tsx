import { createHashRouter, Navigate } from 'react-router-dom';
import { AuthGuard } from './AuthGuard';
import Home from '../pages/Home';
import Chat from '../pages/Chat';
import Settings from '../pages/Settings';
import CharacterDetail from '../pages/CharacterDetail';

/**
 * ⚠️ 必须用 createHashRouter，不能用 createBrowserRouter。
 * WebToApp 打包后的 APK 通过 file:// 或自定义协议加载资源，没有 HTTP 服务器做
 * 路由 fallback：createBrowserRouter 下访问 /chat/abc 会让 WebView 去加载
 * file:///.../chat/abc（不存在）→ 白屏。hash 路由用 #/chat/abc，file:// 下完全工作。
 *
 * 不用 React.lazy：项目小，懒加载收益低，反而增加复杂度。
 */
export const router = createHashRouter([
  { path: '/', element: <Home /> },
  {
    path: '/chat/:characterId',
    element: (
      <AuthGuard>
        <Chat />
      </AuthGuard>
    ),
  },
  { path: '/character/:id', element: <CharacterDetail /> },
  { path: '/settings', element: <Settings /> },
  { path: '*', element: <Navigate to="/" replace /> },
]);
