import React, { useState, useEffect } from "react";
import { ActionButton } from "./ActionButton";
import {
  ActionDefinition,
  ActionResult,
  ExecutionStatus,
} from "../types/settings";

interface ESimSettingsProps {
  onExecuteAction: (
    appId: string,
    action: string,
    params: Record<string, unknown>
  ) => void;
  actionResults: Record<string, ActionResult>;
  executionStatus?: ExecutionStatus | null;
}

export const ESimSettings: React.FC<ESimSettingsProps> = ({
  onExecuteAction,
  actionResults,
  executionStatus,
}) => {
  const [actions, setActions] = useState<Record<string, ActionDefinition>>({});
  const [isConnected, setIsConnected] = useState(false);

  // Mock actions for development - in production these would come from WebSocket messages
  useEffect(() => {
    const mockActions: Record<string, ActionDefinition> = {
      getProfiles: {
        key: "getProfiles",
        displayText: "List eSIM Profiles",
        description: "Retrieve all eSIM profiles on the device",
      },
      getActiveProfile: {
        key: "getActiveProfile",
        displayText: "Get Active Profile",
        description: "Get the currently active eSIM profile",
      },
      getEid: {
        key: "getEid",
        displayText: "Get Device EID",
        description: "Retrieve the device's embedded identity document (EID)",
      },
      enableProfile: {
        key: "enableProfile",
        displayText: "Enable Profile",
        parameters: [
          {
            name: "iccid",
            type: "string",
            required: true,
            description: "Profile ICCID to enable",
          },
        ],
        description: "Enable an eSIM profile by ICCID",
      },
      disableProfile: {
        key: "disableProfile",
        displayText: "Disable Profile",
        parameters: [
          {
            name: "iccid",
            type: "string",
            required: true,
            description: "Profile ICCID to disable",
          },
        ],
        description: "Disable an eSIM profile by ICCID",
      },
      deleteProfile: {
        key: "deleteProfile",
        displayText: "Delete Profile",
        parameters: [
          {
            name: "iccid",
            type: "string",
            required: true,
            description: "Profile ICCID to delete",
          },
        ],
        description: "Permanently delete an eSIM profile",
      },
      setNickname: {
        key: "setNickname",
        displayText: "Set Profile Nickname",
        parameters: [
          {
            name: "iccid",
            type: "string",
            required: true,
            description: "Profile ICCID",
          },
          {
            name: "nickname",
            type: "string",
            required: true,
            description: "New nickname for the profile",
          },
        ],
        description: "Set a custom nickname for an eSIM profile",
      },
      downloadProfile: {
        key: "downloadProfile",
        displayText: "Download Profile",
        parameters: [
          {
            name: "activationCode",
            type: "string",
            required: true,
            description: "eSIM activation code or QR code data",
          },
        ],
        description: "Download a new eSIM profile from activation code",
      },
      downloadAndEnableProfile: {
        key: "downloadAndEnableProfile",
        displayText: "Download & Enable Profile",
        parameters: [
          {
            name: "activationCode",
            type: "string",
            required: true,
            description: "eSIM activation code or QR code data",
          },
        ],
        description: "Download and immediately enable a new eSIM profile",
      },
    };

    setActions(mockActions);
    setIsConnected(true);
  }, []);

  if (!isConnected) {
    return (
      <div className="settings-section">
        <h2>eSIM Management</h2>
        <div className="status-message">Connecting to eSIM service...</div>
      </div>
    );
  }

  if (Object.keys(actions).length === 0) {
    return (
      <div className="settings-section">
        <h2>eSIM Management</h2>
        <div className="status-message">No eSIM actions available</div>
      </div>
    );
  }

  const informationActions = ["getProfiles", "getActiveProfile", "getEid"];
  const managementActions = [
    "enableProfile",
    "disableProfile",
    "deleteProfile",
    "setNickname",
  ];
  const downloadActions = ["downloadProfile", "downloadAndEnableProfile"];

  const renderActionGroup = (title: string, actionKeys: string[]) => {
    const groupActions = actionKeys.filter((key) => actions[key]);
    if (groupActions.length === 0) return null;

    return (
      <div className="action-group">
        <h3>{title}</h3>
        <div className="actions-grid">
          {groupActions.map((actionKey) => (
            <ActionButton
              key={actionKey}
              appId="esim"
              action={actions[actionKey]}
              onExecute={onExecuteAction}
              actionResult={actionResults[`esim.${actionKey}`]}
              executionStatus={executionStatus}
            />
          ))}
        </div>
      </div>
    );
  };

  return (
    <div className="settings-section">
      <h2>eSIM Management</h2>
      <div className="esim-description">
        Manage embedded SIM (eSIM) profiles on your device. You can view
        existing profiles, download new ones, and manage their activation
        status.
      </div>

      {renderActionGroup("Information", informationActions)}
      {renderActionGroup("Profile Management", managementActions)}
      {renderActionGroup("Download Profiles", downloadActions)}

      <div className="esim-help">
        <h4>Usage Notes:</h4>
        <ul>
          <li>
            <strong>Get Profiles:</strong> Shows all eSIM profiles with their
            current status
          </li>
          <li>
            <strong>Get Active Profile:</strong> Shows which profile is
            currently active
          </li>
          <li>
            <strong>Get EID:</strong> Shows the device's unique embedded
            identity
          </li>
          <li>
            <strong>Enable/Disable:</strong> Requires the profile's ICCID (shown
            in profile list)
          </li>
          <li>
            <strong>Download:</strong> Requires an activation code from your
            carrier
          </li>
        </ul>
      </div>
    </div>
  );
};
