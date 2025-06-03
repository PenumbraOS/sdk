use bridge_priv_rs::{connection::SinkConnection, state::AppState};
use log::info;
use std::io::{self};
use tokio::net::TcpListener;

#[tokio::main]
async fn main() -> io::Result<()> {
    init_logging_with_tag("bridge-priv".into());
    let state = AppState::new();
    let listener = TcpListener::bind("127.0.0.1:1720").await?;
    info!("Server listening on {}", listener.local_addr()?);

    loop {
        let (stream, _) = listener.accept().await?;
        let state = state.clone();

        tokio::spawn(async move {
            SinkConnection::listen(stream, state).await;
        });
    }
}

#[cfg(target_os = "android")]
fn init_logging_with_tag(tag: String) {
    use ai_pin_logger::Config;
    use log::LevelFilter;

    let config = Config::default().with_tag(tag);

    ai_pin_logger::init_once(config.with_max_level(LevelFilter::Trace));
}

#[cfg(not(target_os = "android"))]
fn init_logging_with_tag(tag: String) {
    use simple_logger::SimpleLogger;

    let _ = SimpleLogger::new().init();
}
