export class AppError extends Error {
  readonly timestamp: number;

  constructor(message: string) {
    super(message);
    this.name = this.constructor.name;
    this.timestamp = Date.now();
  }
}

export class NetworkError extends AppError {
  constructor(message = '网络连接失败，请检查网络设置') {
    super(message);
  }
}

export class APIError extends AppError {
  readonly statusCode: number;

  constructor(statusCode: number, message: string) {
    super(message);
    this.statusCode = statusCode;
  }
}

export class DataError extends AppError {
  constructor(message = '数据解析异常') {
    super(message);
  }
}

export class UserError extends AppError {
  constructor(message: string) {
    super(message);
  }
}

export enum ErrorCategory {
  NETWORK = 'NETWORK',
  API = 'API',
  DATA = 'DATA',
  USER = 'USER',
  UNKNOWN = 'UNKNOWN',
}

export function classifyError(error: unknown): ErrorCategory {
  if (error instanceof NetworkError) return ErrorCategory.NETWORK;
  if (error instanceof APIError) return ErrorCategory.API;
  if (error instanceof DataError) return ErrorCategory.DATA;
  if (error instanceof UserError) return ErrorCategory.USER;
  return ErrorCategory.UNKNOWN;
}

export function logError(error: AppError, context?: Record<string, unknown>): void {
  const category = classifyError(error);
  const isDev = import.meta.env.DEV;
  const isProd = import.meta.env.PROD;

  // 两个标志都取不到时（非 Vite 运行时）按开发模式处理，保留完整堆栈便于排查
  if (isProd && !isDev) {
    // 生产环境只输出可结构化采集的字段，不带 stack
    console.error(
      '[PROD_ERROR]',
      JSON.stringify({
        name: error.name,
        message: error.message,
        category,
        timestamp: new Date(error.timestamp).toISOString(),
        context,
      })
    );
    return;
  }

  console.error('[DEV_ERROR]', {
    name: error.name,
    message: error.message,
    category,
    timestamp: new Date(error.timestamp).toISOString(),
    stack: error.stack,
    context,
  });
}

type ToastType = 'error' | 'warning' | 'info';

export function handleUserFeedback(error: AppError): void {
  const category = classifyError(error);

  let toastType: ToastType = 'error';
  let message = error.message;

  switch (category) {
    case ErrorCategory.NETWORK:
      toastType = 'warning';
      break;

    case ErrorCategory.USER:
      toastType = 'info';
      break;

    case ErrorCategory.API:
      if ((error as APIError).statusCode === 401) {
        message = 'API Key 无效或已过期，请前往设置页重新配置';
      }
      // 非 401 保留原始 message
      break;

    case ErrorCategory.DATA:
      break; // 保留原始 message

    default: // UNKNOWN
      message = '发生未知错误，请稍后重试';
      break;
  }

  // TODO: 第十章接入 Toast 组件后，替换为 toast[toastType](message)
  console.warn(`[USER_FEEDBACK] ${toastType}: ${message}`);
}
