use crate::{
    error::Result,
    proto::{ClientToServerMessage, ServerToClientMessage},
};
use std::sync::Arc;

use bytes::{Bytes, BytesMut};
use futures::{
    stream::{SplitSink, SplitStream},
    SinkExt, StreamExt,
};
use log::{error, info};
use prost::Message;
use tokio::{
    io::{AsyncRead, AsyncWrite},
    sync::Mutex,
};
use tokio_util::codec::{Framed, LengthDelimitedCodec};

use crate::state::AppState;

pub struct SinkConnection<T>
where
    T: AsyncRead + AsyncWrite + Send,
{
    sink: Arc<Mutex<SplitSink<Framed<T, LengthDelimitedCodec>, Bytes>>>,
    stream: Arc<Mutex<SplitStream<Framed<T, LengthDelimitedCodec>>>>,
}

impl<T> Clone for SinkConnection<T>
where
    T: AsyncRead + AsyncWrite + Send,
{
    fn clone(&self) -> Self {
        Self {
            sink: self.sink.clone(),
            stream: self.stream.clone(),
        }
    }
}

impl<T> SinkConnection<T>
where
    T: AsyncRead + AsyncWrite + Send + 'static,
{
    pub fn new(stream: T) -> Self {
        let framed = Framed::new(
            stream,
            LengthDelimitedCodec::builder().little_endian().new_codec(),
        );
        let (sink, stream) = framed.split();

        Self {
            sink: Arc::new(Mutex::new(sink)),
            stream: Arc::new(Mutex::new(stream)),
        }
    }

    pub async fn listen(stream: T, state: Arc<Mutex<AppState>>) {
        let connection = SinkConnection::new(stream);

        info!("Awaiting messages");
        let mut stream = connection.stream.lock().await;
        while let Some(message) = stream.next().await {
            match message {
                Ok(message) => {
                    let state = state.clone();
                    let sink = connection.clone();
                    tokio::spawn(async move {
                        if let Err(err) = sink.decode_and_process_command(message, state).await {
                            error!("Could not process command: {err}");
                        }
                    });
                }
                Err(err) => error!("Framing error: {err}"),
            }
        }

        let _ = connection.sink.lock().await.close().await;
        info!("Connection closed");
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
        message: BytesMut,
        state: Arc<Mutex<AppState>>,
    ) -> Result<()> {
        let message = ClientToServerMessage::decode(message)?;

        state.lock().await.handle_command(message, self).await
    }
}
