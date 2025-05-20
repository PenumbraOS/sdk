use std::sync::Arc;

use http::HttpState;
use tokio::sync::Mutex;

use crate::{
    connection::SinkConnection,
    error::{Error, Result},
    proto::{client_to_server_message, ClientToServerMessage},
};

mod http;

pub struct AppState {
    http: HttpState,
}

impl AppState {
    pub fn new() -> Arc<Mutex<Self>> {
        Arc::new(Mutex::new(Self {
            http: HttpState::new(),
        }))
    }

    pub async fn handle_command(
        &mut self,
        message: ClientToServerMessage,
        connection: SinkConnection,
    ) -> Result<()> {
        let request_id = message
            .origin
            .ok_or_else(|| Error::ProtocolHandleError("No request ID provided".into()))?
            .id;
        let payload = message
            .payload
            .ok_or_else(|| Error::ProtocolHandleError("No payload provided".into()))?;

        match payload {
            client_to_server_message::Payload::HttpRequest(request) => {
                self.http.request(request_id, request, connection).await?;
            }
            client_to_server_message::Payload::WsOpenRequest(_request) => {
                todo!()
            }
            client_to_server_message::Payload::WsMessageToServer(_request) => {
                todo!()
            }
            client_to_server_message::Payload::WsCloseRequest(_request) => {
                todo!()
            }
        }

        Ok(())
    }
}
