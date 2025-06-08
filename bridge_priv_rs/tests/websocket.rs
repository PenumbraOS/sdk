use bridge_priv_rs::{connection::SinkConnection, proto, state::websocket::WebSocketState};
use futures::{SinkExt, StreamExt};
use prost::Message;
use tcp_utils::MockTcpStream;
use tokio::net::{TcpListener, TcpStream};
use tokio_tungstenite::{accept_async, WebSocketStream};

mod tcp_utils;

// Simple WebSocket echo server for testing
async fn start_echo_server(port: u16) -> tokio::task::JoinHandle<()> {
    let listener = TcpListener::bind(format!("127.0.0.1:{}", port))
        .await
        .unwrap();

    tokio::spawn(async move {
        while let Ok((stream, _)) = listener.accept().await {
            let ws_stream = accept_async(stream).await.unwrap();
            echo_connection(ws_stream).await;
        }
    })
}

async fn echo_connection(ws_stream: WebSocketStream<TcpStream>) {
    let (mut ws_sender, mut ws_receiver) = ws_stream.split();
    while let Some(msg) = ws_receiver.next().await {
        let msg = msg.unwrap();
        ws_sender.send(msg).await.unwrap();
    }
}

#[tokio::test]
async fn test_websocket_open_and_echo() {
    let port = 12345;
    let _server = start_echo_server(port).await;

    let (stream, mut handle) = MockTcpStream::new();
    let connection = SinkConnection::new(stream);

    let request = proto::WebSocketOpenProxyRequest {
        url: format!("ws://127.0.0.1:{}/echo", port).into(),
        headers: vec![],
    };

    let ws_state = WebSocketState::new();
    let result = ws_state.open("123".into(), request, connection).await;
    println!("{result:?}");
    assert!(result.is_ok(), "WebSocket open should not crash");

    // Verify connection opened
    let written = handle.take_written().await;
    let response = proto::ServerToClientMessage::decode(&written[4..]).unwrap();
    assert_eq!(response.origin.unwrap().id, "123");
    match response.payload.unwrap() {
        proto::server_to_client_message::Payload::WsOpened(_) => (),
        _ => panic!("Expected WebSocket opened message"),
    }

    // Test message echo
    let test_msg = "test message";
    let message = proto::WebSocketProxyMessage {
        r#type: proto::WebSocketMessageType::Text.into(),
        data: test_msg.as_bytes().to_vec(),
    };
    let result = ws_state.send_message("123".into(), message).await;
    assert!(result.is_ok(), "Message send should not crash");

    // Verify echo response
    let written = handle.take_written().await;
    let response = proto::ServerToClientMessage::decode(&written[4..]).unwrap();
    assert_eq!(response.origin.unwrap().id, "123");
    match response.payload.unwrap() {
        proto::server_to_client_message::Payload::WsMessageFromServer(msg) => {
            assert_eq!(msg.r#type(), proto::WebSocketMessageType::Text);
            assert_eq!(String::from_utf8(msg.data).unwrap(), test_msg);
        }
        _ => panic!("Expected WebSocket message response"),
    }
}

#[tokio::test]
async fn test_websocket_open_failure() {
    let (stream, _) = MockTcpStream::new();
    let connection = SinkConnection::new(stream);

    let request = proto::WebSocketOpenProxyRequest {
        url: "ws://invalid-url".into(),
        headers: vec![],
    };

    let ws_state = WebSocketState::new();
    let result = ws_state.open("123".into(), request, connection).await;
    assert!(result.is_err(), "Open should fail");
}
