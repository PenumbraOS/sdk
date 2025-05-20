use crate::{error::Result, proto::ClientToServerMessage};
use std::sync::Arc;

use bytes::{Bytes, BytesMut};
use futures::{stream::SplitSink, StreamExt};
use log::error;
use prost::Message;
use tokio::{io, net::TcpStream, sync::Mutex};
use tokio_util::codec::{Framed, LengthDelimitedCodec};

use crate::state::AppState;

pub type SinkConnection = Arc<Mutex<SplitSink<Framed<TcpStream, LengthDelimitedCodec>, Bytes>>>;

pub async fn handle_connection(stream: TcpStream, state: Arc<Mutex<AppState>>) -> Result<()> {
    let framed = Framed::new(stream, LengthDelimitedCodec::new());
    let (sink, mut stream) = framed.split();

    let sink = Arc::new(Mutex::new(sink));

    while let Some(message) = stream.next().await {
        let state = state.clone();
        let sink = sink.clone();
        tokio::spawn(async move {
            if let Err(err) = decode_and_process_command(message, state, sink).await {
                error!("Could not process command: {err}");
            }
        });
    }

    Ok(())
}

async fn decode_and_process_command(
    message: std::result::Result<BytesMut, io::Error>,
    state: Arc<Mutex<AppState>>,
    connection: SinkConnection,
) -> Result<()> {
    let bytes = message?;
    let message = ClientToServerMessage::decode(bytes)?;

    state.lock().await.handle_command(message, connection).await
}
