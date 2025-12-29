import { useState, useEffect } from 'react';
import type { ObjectType, Property, Instance } from '../api/client';
import { instanceApi } from '../api/client';
import { XMarkIcon } from '@heroicons/react/24/outline';

interface InstanceFormProps {
  objectType: ObjectType;
  instance?: Instance | null;
  onClose: () => void;
}

export default function InstanceForm({ objectType, instance, onClose }: InstanceFormProps) {
  const [formData, setFormData] = useState<Record<string, any>>({});
  const [loading, setLoading] = useState(false);
  const [errors, setErrors] = useState<Record<string, string>>({});

  useEffect(() => {
    if (instance) {
      const data: Record<string, any> = {};
      objectType.properties.forEach((prop) => {
        if (instance[prop.name] !== undefined) {
          data[prop.name] = instance[prop.name];
        } else if (prop.default_value !== null && prop.default_value !== undefined) {
          data[prop.name] = prop.default_value;
        }
      });
      setFormData(data);
    } else {
      const data: Record<string, any> = {};
      objectType.properties.forEach((prop) => {
        if (prop.default_value !== null && prop.default_value !== undefined) {
          data[prop.name] = prop.default_value;
        }
      });
      setFormData(data);
    }
  }, [instance, objectType]);

  const validateField = (prop: Property, value: any): string | null => {
    if (prop.required && (value === null || value === undefined || value === '')) {
      return `${prop.name} is required`;
    }

    if (value === null || value === undefined || value === '') {
      return null;
    }

    // Type validation
    switch (prop.data_type) {
      case 'int':
        if (typeof value !== 'number' && isNaN(Number(value))) {
          return `${prop.name} must be a number`;
        }
        break;
      case 'float':
        if (typeof value !== 'number' && isNaN(Number(value))) {
          return `${prop.name} must be a number`;
        }
        break;
      case 'bool':
        if (typeof value !== 'boolean') {
          return `${prop.name} must be a boolean`;
        }
        break;
    }

    // Constraint validation
    if (prop.constraints) {
      if (prop.data_type === 'string' && typeof value === 'string') {
        if (prop.constraints.min_length && value.length < (prop.constraints.min_length as number)) {
          return `${prop.name} must be at least ${prop.constraints.min_length} characters`;
        }
        if (prop.constraints.max_length && value.length > (prop.constraints.max_length as number)) {
          return `${prop.name} must be at most ${prop.constraints.max_length} characters`;
        }
        if (prop.constraints.pattern) {
          const regex = new RegExp(prop.constraints.pattern as string);
          if (!regex.test(value)) {
            return `${prop.name} format is invalid`;
          }
        }
      }
      if ((prop.data_type === 'int' || prop.data_type === 'float') && typeof value === 'number') {
        if (prop.constraints.min && value < (prop.constraints.min as number)) {
          return `${prop.name} must be at least ${prop.constraints.min}`;
        }
        if (prop.constraints.max && value > (prop.constraints.max as number)) {
          return `${prop.name} must be at most ${prop.constraints.max}`;
        }
      }
    }

    return null;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrors({});

    // Validate all fields
    const newErrors: Record<string, string> = {};
    objectType.properties.forEach((prop) => {
      const error = validateField(prop, formData[prop.name]);
      if (error) {
        newErrors[prop.name] = error;
      }
    });

    if (Object.keys(newErrors).length > 0) {
      setErrors(newErrors);
      return;
    }

    setLoading(true);
    try {
      if (instance) {
        await instanceApi.update(objectType.name, instance.id, formData);
      } else {
        await instanceApi.create(objectType.name, formData);
      }
      onClose();
    } catch (error: any) {
      console.error('Failed to save instance:', error);
      if (error.response?.data?.message) {
        alert(error.response.data.message);
      } else {
        alert('Failed to save instance');
      }
    } finally {
      setLoading(false);
    }
  };

  const handleChange = (prop: Property, value: any) => {
    // Type conversion
    let convertedValue = value;
    if (prop.data_type === 'int' || prop.data_type === 'float') {
      if (value === '') {
        convertedValue = undefined;
      } else {
        convertedValue = prop.data_type === 'int' ? parseInt(value, 10) : parseFloat(value);
        if (isNaN(convertedValue)) {
          convertedValue = value;
        }
      }
    } else if (prop.data_type === 'bool') {
      convertedValue = value === 'true' || value === true;
    } else if (prop.data_type === 'json') {
      try {
        convertedValue = typeof value === 'string' ? JSON.parse(value) : value;
      } catch {
        convertedValue = value;
      }
    }

    setFormData({ ...formData, [prop.name]: convertedValue });
    // Clear error for this field
    if (errors[prop.name]) {
      const newErrors = { ...errors };
      delete newErrors[prop.name];
      setErrors(newErrors);
    }
  };

  const renderField = (prop: Property) => {
    const value = formData[prop.name] ?? '';
    const error = errors[prop.name];

    switch (prop.data_type) {
      case 'bool':
        return (
          <select
            value={String(value)}
            onChange={(e) => handleChange(prop, e.target.value)}
            className={`mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-blue-500 focus:ring-blue-500 ${
              error ? 'border-red-300' : ''
            }`}
          >
            <option value="">-- Select --</option>
            <option value="true">True</option>
            <option value="false">False</option>
          </select>
        );
      case 'json':
        return (
          <textarea
            value={typeof value === 'object' ? JSON.stringify(value, null, 2) : value}
            onChange={(e) => handleChange(prop, e.target.value)}
            rows={4}
            className={`mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-blue-500 focus:ring-blue-500 font-mono text-sm ${
              error ? 'border-red-300' : ''
            }`}
            placeholder="{}"
          />
        );
      default:
        return (
          <input
            type={prop.data_type === 'int' || prop.data_type === 'float' ? 'number' : 'text'}
            value={value}
            onChange={(e) => handleChange(prop, e.target.value)}
            className={`mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-blue-500 focus:ring-blue-500 ${
              error ? 'border-red-300' : ''
            }`}
          />
        );
    }
  };

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg shadow-xl max-w-2xl w-full max-h-[90vh] overflow-y-auto m-4">
        <div className="sticky top-0 bg-white border-b border-gray-200 px-6 py-4 flex items-center justify-between">
          <h2 className="text-xl font-bold text-gray-900">
            {instance ? 'Edit' : 'Create'} {objectType.name}
          </h2>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600"
          >
            <XMarkIcon className="w-6 h-6" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="p-6">
          <div className="space-y-4">
            {objectType.properties.map((prop) => (
              <div key={prop.name}>
                <label className="block text-sm font-medium text-gray-700">
                  {prop.name}
                  {prop.required && <span className="text-red-500 ml-1">*</span>}
                  <span className="text-gray-500 ml-2 text-xs">({prop.data_type})</span>
                </label>
                {prop.description && (
                  <p className="text-xs text-gray-500 mt-0.5">{prop.description}</p>
                )}
                {renderField(prop)}
                {errors[prop.name] && <p className="mt-1 text-sm text-red-600">{errors[prop.name]}</p>}
              </div>
            ))}
          </div>

          <div className="mt-6 flex items-center justify-end gap-3">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 border border-gray-300 rounded-lg text-gray-700 hover:bg-gray-50"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={loading}
              className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {loading ? 'Saving...' : instance ? 'Update' : 'Create'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

