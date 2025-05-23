use bridge_priv_rs::{connection::SinkConnection, state::AppState};
use std::io::{self};
use tokio::net::TcpListener;

#[tokio::main]
async fn main() -> io::Result<()> {
    let state = AppState::new();
    let listener = TcpListener::bind("127.0.0.1:1720").await?;
    println!("Server listening on {}", listener.local_addr()?);

    loop {
        let (stream, _) = listener.accept().await?;
        let state = state.clone();

        tokio::spawn(async move {
            SinkConnection::listen(stream, state).await;
        });
    }
}
