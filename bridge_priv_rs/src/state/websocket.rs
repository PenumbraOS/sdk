use crate::{
    connection::SinkConnection,
    error::{Error, Result},
    proto::{
        server_to_client_message, RequestOrigin, ServerToClientMessage, WebSocketCloseProxyRequest,
        WebSocketError, WebSocketMessageType, WebSocketOpenProxyRequest, WebSocketOpenedResponse,
        WebSocketProxyMessage,
    },
    state::util::header_map,
};
use futures::{
    stream::{SplitSink, SplitStream},
    SinkExt, StreamExt, TryStreamExt,
};
use log::{error, info};
use reqwest::Client;
use reqwest_websocket::{Message, RequestBuilderExt, WebSocket};
use std::collections::HashMap;
use tokio::{
    io::{AsyncRead, AsyncWrite},
    sync::Mutex,
};
use tokio_util::sync::CancellationToken;

pub struct WebSocketState {
    client: Client,
    connections: Mutex<HashMap<String, WebSocketConnection>>,
}

struct WebSocketConnection {
    #[allow(dead_code)]
    id: String,
    sink: SplitSink<WebSocket, Message>,
    shutdown_token: CancellationToken,
}

impl WebSocketState {
    pub fn new() -> Self {
        Self {
            client: Client::new(),
            connections: Mutex::new(HashMap::new()),
        }
    }

    pub async fn open<T>(
        &self,
        client_ws_id: String,
        request: WebSocketOpenProxyRequest,
        connection: SinkConnection<T>,
    ) -> Result<()>
    where
        T: AsyncRead + AsyncWrite + Send + 'static,
    {
        let url = request.url;
        let headers = header_map(request.headers)?;

        info!("Opening WebSocket connection to {}", url);
        let response = self
            .client
            .get(&url)
            .headers(headers)
            .upgrade()
            .send()
            .await?;

        match response.into_websocket().await {
            Ok(websocket) => {
                info!("WebSocket connection established");
                let opened_msg = ServerToClientMessage {
                    origin: Some(RequestOrigin {
                        id: client_ws_id.clone(),
                    }),
                    payload: Some(server_to_client_message::Payload::WsOpened(
                        WebSocketOpenedResponse { headers: vec![] },
                    )),
                };
                connection.write(opened_msg).await?;

                let (sink, stream) = websocket.split();

                let shutdown_token = CancellationToken::new();
                self.spawn_message_worker(
                    client_ws_id.clone(),
                    stream,
                    shutdown_token.clone(),
                    connection.clone(),
                );

                self.connections.lock().await.insert(
                    client_ws_id.clone(),
                    WebSocketConnection {
                        id: client_ws_id,
                        sink,
                        shutdown_token,
                    },
                );
                Ok(())
            }
            Err(err) => {
                error!("WebSocket connection failed: {}", err);
                let err_msg = ServerToClientMessage {
                    origin: Some(RequestOrigin { id: client_ws_id }),
                    payload: Some(server_to_client_message::Payload::WsError(WebSocketError {
                        error_message: err.to_string(),
                    })),
                };
                connection.write(err_msg).await?;
                Ok(())
            }
        }
    }

    pub async fn send_message(
        &self,
        client_ws_id: String,
        message: WebSocketProxyMessage,
    ) -> Result<()> {
        let mut connections = self.connections.lock().await;
        if let Some(websocket) = connections.get_mut(&client_ws_id) {
            let message = match message.r#type() {
                WebSocketMessageType::Text => {
                    let data = String::from_utf8(message.data).or_else(|err| {
                        Err(Error::Unknown(format!(
                            "Could not decode WebSocket text message for sending: {err}"
                        )))
                    })?;
                    Message::Text(data)
                }
                WebSocketMessageType::Binary => Message::Binary(message.data.into()),
            };

            websocket.sink.send(message).await?;
            Ok(())
        } else {
            Err(Error::ProtocolHandleError(
                "WebSocket connection not found".into(),
            ))
        }
    }

    pub async fn close(
        &self,
        client_ws_id: String,
        _request: WebSocketCloseProxyRequest,
    ) -> Result<()> {
        if let Some(mut websocket) = self.connections.lock().await.remove(&client_ws_id) {
            websocket.sink.close().await?;
            websocket.shutdown_token.cancel();
        }

        Ok(())
    }

    fn spawn_message_worker<T>(
        &self,
        id: String,
        mut stream: SplitStream<WebSocket>,
        shutdown_token: CancellationToken,
        mut connection: SinkConnection<T>,
    ) where
        T: AsyncRead + AsyncWrite + Send + 'static,
    {
        tokio::spawn(async move {
            loop {
                let shutdown_token = shutdown_token.clone();
                tokio::select! {
                    _ = shutdown_token.cancelled() => {
                        return;
                    }
                    message_wrapper = stream.try_next() => match message_wrapper {
                        Ok(Some(message)) => {
                            let _ = message_callback(id.clone(), message, &mut connection).await;
                        }
                        Ok(None) => {
                            error!("WebSocket message recieve error: Unknown");
                            return;
                        }
                        Err(err) => {
                            error!("WebSocket message recieve error: {err}");
                            return;
                        }
                    }
                }
            }
        });
    }
}

async fn message_callback<T>(
    id: String,
    message: Message,
    connection: &mut SinkConnection<T>,
) -> Result<()>
where
    T: AsyncRead + AsyncWrite + Send + 'static,
{
    let data = match message {
        Message::Text(text) => Some((
            WebSocketMessageType::Text,
            text.as_bytes().iter().copied().collect(),
        )),
        Message::Binary(bytes) => Some((WebSocketMessageType::Binary, bytes)),
        // Probably don't care about these
        // Message::Ping(bytes) => todo!(),
        // Message::Pong(bytes) => todo!(),
        // Message::Close { code, reason } => todo!(),
        _ => None,
    };

    if let Some((r#type, data)) = data {
        let message = ServerToClientMessage {
            origin: Some(RequestOrigin { id }),
            payload: Some(server_to_client_message::Payload::WsMessageFromServer(
                WebSocketProxyMessage {
                    r#type: r#type.into(),
                    data: data.to_vec(),
                },
            )),
        };
        connection.write(message).await?;
    }

    Ok(())
}
