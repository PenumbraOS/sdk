import React from "react";

interface TextInputProps {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  disabled?: boolean;
  style?: React.CSSProperties;
}

export const TextInput: React.FC<TextInputProps> = ({
  value,
  onChange,
  placeholder = "",
  disabled = false,
  style = {},
}) => {
  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    onChange(e.target.value);
  };

  const defaultStyle: React.CSSProperties = {
    padding: "4px 8px",
    border: "1px solid #bdc3c7",
    borderRadius: "3px",
    fontSize: "0.9rem",
    minWidth: "120px",
    maxWidth: "200px",
    outline: "none",
    ...style,
  };

  return (
    <input
      type="text"
      value={value}
      onChange={handleChange}
      placeholder={placeholder}
      disabled={disabled}
      style={defaultStyle}
      onFocus={(e) => (e.target.style.borderColor = "#3498db")}
      onBlur={(e) => (e.target.style.borderColor = "#bdc3c7")}
    />
  );
};
