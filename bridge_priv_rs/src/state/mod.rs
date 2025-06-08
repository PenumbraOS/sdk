use std::sync::Arc;

use http::HttpState;
use log::info;
use tokio::{
    io::{AsyncRead, AsyncWrite},
    sync::Mutex,
};

use crate::{
    connection::SinkConnection,
    error::{Error, Result},
    proto::{client_to_server_message, ClientToServerMessage},
};

pub mod http;
mod util;
pub mod websocket;

pub struct AppState {
    http: HttpState,
    ws: websocket::WebSocketState,
}

impl AppState {
    pub fn new() -> Arc<Mutex<Self>> {
        Arc::new(Mutex::new(Self {
            http: HttpState::new(),
            ws: websocket::WebSocketState::new(),
        }))
    }

    pub async fn handle_command<T>(
        &mut self,
        message: ClientToServerMessage,
        connection: SinkConnection<T>,
    ) -> Result<()>
    where
        T: AsyncRead + AsyncWrite + Send + 'static,
    {
        let request_id = message
            .origin
            .ok_or_else(|| Error::ProtocolHandleError("No request ID provided".into()))?
            .id;
        let payload = message
            .payload
            .ok_or_else(|| Error::ProtocolHandleError("No payload provided".into()))?;

        info!("Processing {payload:?}");

        match payload {
            client_to_server_message::Payload::HttpRequest(request) => {
                self.http.request(request_id, request, connection).await?;
            }
            client_to_server_message::Payload::WsOpenRequest(request) => {
                self.ws.open(request_id, request, connection).await?;
            }
            client_to_server_message::Payload::WsMessageToServer(request) => {
                self.ws.send_message(request_id, request).await?;
            }
            client_to_server_message::Payload::WsCloseRequest(request) => {
                self.ws.close(request_id, request).await?;
            }
        }

        Ok(())
    }
}
