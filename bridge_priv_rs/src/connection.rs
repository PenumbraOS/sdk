use crate::{
    error::Result,
    proto::{ClientToServerMessage, ServerToClientMessage},
};
use std::sync::Arc;

use bytes::{Bytes, BytesMut};
use futures::{stream::SplitSink, SinkExt, StreamExt};
use log::error;
use prost::Message;
use tokio::{
    io::{self, AsyncRead, AsyncWrite},
    sync::Mutex,
};
use tokio_util::codec::{Framed, LengthDelimitedCodec};

use crate::state::AppState;

pub struct SinkConnection<T>
where
    T: AsyncRead + AsyncWrite + Send,
{
    sink: Arc<Mutex<SplitSink<Framed<T, LengthDelimitedCodec>, Bytes>>>,
}

impl<T> Clone for SinkConnection<T>
where
    T: AsyncRead + AsyncWrite + Send,
{
    fn clone(&self) -> Self {
        Self {
            sink: self.sink.clone(),
        }
    }
}

impl<T> SinkConnection<T>
where
    T: AsyncRead + AsyncWrite + Send + 'static,
{
    pub async fn listen(stream: T, state: Arc<Mutex<AppState>>) {
        let framed = Framed::new(stream, LengthDelimitedCodec::new());
        let (sink, mut stream) = framed.split();

        let sink = SinkConnection {
            sink: Arc::new(Mutex::new(sink)),
        };

        while let Some(message) = stream.next().await {
            let state = state.clone();
            let sink = sink.clone();
            tokio::spawn(async move {
                if let Err(err) = sink.decode_and_process_command(message, state).await {
                    error!("Could not process command: {err}");
                }
            });
        }
    }

    pub async fn write(&self, message: ServerToClientMessage) -> Result<()> {
        self.sink
            .lock()
            .await
            .send(Bytes::from(message.encode_to_vec()))
            .await?;

        Ok(())
    }

    async fn decode_and_process_command(
        self,
        message: std::result::Result<BytesMut, io::Error>,
        state: Arc<Mutex<AppState>>,
    ) -> Result<()> {
        let bytes = message?;
        let message = ClientToServerMessage::decode(bytes)?;

        state.lock().await.handle_command(message, self).await
    }
}
