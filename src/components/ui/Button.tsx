import React from 'react';

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger';
type Size = 'sm' | 'md' | 'lg';

interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: Size;
}

const BASE_CLASSES =
  'inline-flex items-center justify-center rounded font-medium transition-all active:scale-95 disabled:opacity-50 disabled:cursor-not-allowed';

const VARIANT_CLASSES: Record<Variant, string> = {
  primary: 'bg-primary-600 text-white hover:bg-primary-700',
  secondary:
    'bg-surface-200 text-text-primary hover:bg-surface-300 dark:bg-surface-700 dark:hover:bg-surface-800 dark:text-surface-100',
  ghost:
    'hover:bg-surface-100 text-text-secondary dark:hover:bg-surface-800 dark:text-surface-100',
  // 红是语义色：项目 accent 只有 500 一档，做不出 hover，暂留 Tailwind 默认红
  danger: 'bg-red-600 text-white hover:bg-red-700',
};

const SIZE_CLASSES: Record<Size, string> = {
  sm: 'px-2 py-1 text-sm',
  md: 'px-4 py-2 text-base',
  lg: 'px-6 py-3 text-lg',
};

export const Button: React.FC<ButtonProps> = ({
  variant = 'primary',
  size = 'md',
  className,
  type = 'button',
  ...props
}) => {
  const classes = [BASE_CLASSES, VARIANT_CLASSES[variant], SIZE_CLASSES[size], className]
    .filter(Boolean)
    .join(' ');

  return <button type={type} className={classes} {...props} />;
};
