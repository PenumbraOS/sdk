use std::time::Duration;

use bridge_priv_rs::{
    connection::SinkConnection,
    proto::{self, ClientToServerMessage, RequestOrigin},
    state::AppState,
};
use ntest::timeout;
use prost::Message;
use tcp_utils::{send_proto, MockTcpStream};
use tokio::time::sleep;

mod tcp_utils;

#[tokio::test]
#[timeout(2000)]
async fn test_connection_handling() {
    simple_logger::SimpleLogger::new().env().init().unwrap();

    let (stream, mut handle) = MockTcpStream::new();
    let state = AppState::new();

    let req = ClientToServerMessage {
        origin: Some(RequestOrigin { id: "123".into() }),
        payload: Some(proto::client_to_server_message::Payload::HttpRequest(
            proto::HttpProxyRequest {
                url: "http://example.com/test".into(),
                method: "GET".into(),
                headers: vec![],
                body: vec![],
            },
        )),
    };

    send_proto(&mut handle, req).await;

    tokio::spawn(async move {
        SinkConnection::listen(stream, state).await;
    });

    let written = handle.take_written().await;
    // TODO: Properly strip length prefix for tests
    let response = proto::ServerToClientMessage::decode(&written[4..]).unwrap();
    assert_eq!(response.origin.unwrap().id, "123");
}

#[tokio::test]
async fn test_invalid_message() {
    // simple_logger::SimpleLogger::new().env().init().unwrap();
    testing_logger::setup();

    let (stream, mut handle) = MockTcpStream::new();
    let state = AppState::new();

    // Send invalid message
    handle.push_bytes(&[1]).await;

    tokio::spawn(async move {
        SinkConnection::listen(stream, state).await;
    });

    // TODO: Make proper await of logs
    sleep(Duration::from_millis(1000)).await;

    testing_logger::validate(|logs| {
        assert_eq!(logs.len(), 1);
        assert_eq!(
            logs[0].body,
            "Could not process command: Protocol decode error"
        )
    });
}
