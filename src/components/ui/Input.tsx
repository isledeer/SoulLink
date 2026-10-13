import React from 'react';

interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  error?: string;
}

const BASE_CLASSES = 'w-full p-2 border rounded focus:outline-none focus:ring-2';
const ERROR_CLASSES = 'border-red-500 focus:ring-red-500';
const NORMAL_CLASSES = 'border-gray-300 focus:ring-blue-500';

export const Input: React.FC<InputProps> = ({ error, className, ...props }) => {
  const classes = [BASE_CLASSES, error ? ERROR_CLASSES : NORMAL_CLASSES, className]
    .filter(Boolean)
    .join(' ');

  return (
    <div className="w-full">
      <input className={classes} {...props} />
      {error && <p className="text-red-500 text-xs mt-1">{error}</p>}
    </div>
  );
};
