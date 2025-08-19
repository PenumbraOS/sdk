import React, { useState } from "react";
import {
  ActionDefinition,
  ActionParameter,
  ActionResult,
  LogEntry,
  ExecutionStatus,
} from "../types/settings";
import { TextInput } from "./TextInput";

interface ActionButtonProps {
  appId: string;
  action: ActionDefinition;
  onExecute: (
    appId: string,
    action: string,
    params: Record<string, unknown>
  ) => void;
  actionResult?: ActionResult;
  executionStatus?: ExecutionStatus | null;
}

export const ActionButton: React.FC<ActionButtonProps> = ({
  appId,
  action,
  onExecute,
  actionResult,
  executionStatus,
}) => {
  const [isExecutingLocally, setIsExecutingLocally] = useState(false);
  const [showParameters, setShowParameters] = useState(false);
  const [paramValues, setParamValues] = useState<Record<string, unknown>>({});
  const [lastResult, setLastResult] = useState<ActionResult | null>(null);

  const hasParameters = action.parameters && action.parameters.length > 0;

  const handleExecute = () => {
    if (hasParameters && !showParameters) {
      setShowParameters(true);
      return;
    }

    setIsExecutingLocally(true);
    onExecute(appId, action.key, paramValues);
  };

  const handleParameterChange = (paramName: string, value: unknown) => {
    setParamValues((prev) => ({
      ...prev,
      [paramName]: value,
    }));
  };

  const getInputConfig = (param: ActionParameter) => {
    const currentValue = paramValues[param.name] ?? param.defaultValue ?? "";

    const baseConfigs = {
      boolean: {
        type: "checkbox" as const,
        checked: Boolean(currentValue),
        onChange: (e: React.ChangeEvent<HTMLInputElement>) =>
          handleParameterChange(param.name, e.target.checked),
      },
      integer: {
        type: "number" as const,
        value: String(currentValue),
        onChange: (e: React.ChangeEvent<HTMLInputElement>) =>
          handleParameterChange(param.name, parseInt(e.target.value) || 0),
      },
      float: {
        type: "number" as const,
        step: "0.01",
        value: String(currentValue),
        onChange: (e: React.ChangeEvent<HTMLInputElement>) =>
          handleParameterChange(param.name, parseFloat(e.target.value) || 0),
      },
      string: {
        type: "string" as const,
        value: String(currentValue),
        placeholder: param.description,
        onChange: (value: string) => handleParameterChange(param.name, value),
      },
    };

    return (
      baseConfigs[param.type as keyof typeof baseConfigs] || baseConfigs.string
    );
  };

  const renderParameterInput = (param: ActionParameter) => {
    const inputConfig = getInputConfig(param);

    return (
      <label key={param.name} className="parameter-input">
        <span className="parameter-label">
          {param.name}
          {param.required && <span className="required">*</span>}
        </span>
        {inputConfig.type === "string" ? (
          <TextInput
            value={inputConfig.value}
            onChange={inputConfig.onChange}
            placeholder={inputConfig.placeholder}
          />
        ) : (
          <input {...inputConfig} />
        )}
        {param.description && (
          <span className="parameter-description">{param.description}</span>
        )}
      </label>
    );
  };

  const renderLogs = (logs: LogEntry[]) => {
    if (!logs || !Array.isArray(logs) || logs.length === 0) {
      return null;
    }

    return (
      <div className="action-logs">
        <h4>Execution Log:</h4>
        <div className="log-entries">
          {logs.map((log, index) => {
            const levelClass = `log-${log.level.toLowerCase()}`;
            return (
              <div key={index} className={`log-entry ${levelClass}`}>
                <span className="log-timestamp">
                  {new Date(log.timestamp).toLocaleTimeString()}
                </span>
                <span className={`log-level ${levelClass}`}>{log.level}</span>
                <span className="log-message">{log.message}</span>
              </div>
            );
          })}
        </div>
      </div>
    );
  };

  // Check if this specific action is currently executing based on global execution status
  const isCurrentlyExecuting =
    (executionStatus != null &&
      executionStatus.providerId === appId &&
      executionStatus.actionName === action.key) ||
    isExecutingLocally;

  React.useEffect(() => {
    // When we receive an external action result, update our local state and stop executing
    if (actionResult) {
      setLastResult(actionResult);
      setIsExecutingLocally(false);
    }
  }, [actionResult]);

  return (
    <div className="action-button-container">
      <div className="action-header">
        <button
          className={`action-button ${isCurrentlyExecuting ? "executing" : ""}`}
          onClick={handleExecute}
          disabled={isCurrentlyExecuting}
        >
          {isCurrentlyExecuting ? "Executing..." : action.displayText}
        </button>
        {action.description && (
          <span className="action-description">{action.description}</span>
        )}
      </div>

      {showParameters && hasParameters && (
        <div className="parameter-form">
          <h4>Parameters:</h4>
          {action.parameters!.map((param) => renderParameterInput(param))}
          <div className="parameter-actions">
            <button
              className="execute-with-params"
              onClick={handleExecute}
              disabled={isCurrentlyExecuting}
            >
              {isCurrentlyExecuting ? "Executing..." : "Execute"}
            </button>
            <button
              className="cancel-params"
              onClick={() => setShowParameters(false)}
            >
              Cancel
            </button>
          </div>
        </div>
      )}

      {lastResult && (
        <div
          className={`action-result ${
            lastResult.success ? "success" : "error"
          }`}
        >
          <div className="result-header">
            <span
              className={`result-status ${
                lastResult.success ? "success" : "error"
              }`}
            >
              {lastResult.success ? "✓" : "✗"}
            </span>
            <span className="result-message">
              {lastResult.message ||
                (lastResult.success
                  ? "Action completed successfully"
                  : "Action failed")}
            </span>
          </div>

          {lastResult.data && Object.keys(lastResult.data).length > 0 && (
            <div className="result-data">
              <h4>Result Data:</h4>
              <pre>{JSON.stringify(lastResult.data, null, 2)}</pre>
            </div>
          )}

          {renderLogs(lastResult.logs || [])}
        </div>
      )}
    </div>
  );
};
