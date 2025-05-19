package com.penumbraos.bridge;

interface INetworkService {
    /**
     * Fetches the content of a given URL.
     * @param url The URL to fetch.
     * @return The content of the URL as a String, or an error message.
     */
    String fetchUrl(String url);
}
