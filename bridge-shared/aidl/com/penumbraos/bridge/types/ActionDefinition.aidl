package com.penumbraos.bridge.types;

import com.penumbraos.bridge.types.ActionParameter;

parcelable ActionDefinition {
    String key;
    String displayText;
    String description;
    List<ActionParameter> parameters;
}