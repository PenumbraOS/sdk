package com.penumbraos.bridge;

interface IDnsProvider {
    String lookup(String hostname);
}