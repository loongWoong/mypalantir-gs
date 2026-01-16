import React from 'react';

export interface ButtonGroupOption<T extends string> {
  value: T;
  label: string;
  icon?: React.ComponentType<{ className?: string }>;
  title?: string;
}

interface ButtonGroupProps<T extends string> {
  options: ButtonGroupOption<T>[];
  value: T;
  onChange: (value: T) => void;
  className?: string;
  activeClassName?: string;
}

export default function ButtonGroup<T extends string>({
  options,
  value,
  onChange,
  className = '',
  activeClassName = 'bg-blue-600 text-white',
}: ButtonGroupProps<T>) {
  return (
    <div className={`flex items-center gap-2 bg-gray-100 rounded-lg p-1 ${className}`}>
      {options.map((option) => {
        const Icon = option.icon;
        const isActive = value === option.value;
        return (
          <button
            key={option.value}
            onClick={() => onChange(option.value)}
            className={`flex items-center px-4 py-2 rounded-md transition-all ${
              isActive
                ? `${activeClassName} shadow-sm font-medium`
                : 'bg-transparent text-gray-600 hover:bg-gray-200'
            }`}
            title={option.title || option.label}
            type="button"
          >
            {Icon && <Icon className="w-5 h-5 mr-2" />}
            {option.label}
          </button>
        );
      })}
    </div>
  );
}
