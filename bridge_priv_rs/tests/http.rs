use bridge_priv_rs::{connection::SinkConnection, proto, state::http::HttpState};
use prost::Message;
use tcp_utils::MockTcpStream;

mod tcp_utils;

#[tokio::test]
async fn test_http_request_handling() {
    let (stream, mut handle) = MockTcpStream::new();
    let connection = SinkConnection::new(stream);

    let request = proto::HttpProxyRequest {
        url: "http://example.com/test".into(),
        method: "GET".into(),
        headers: vec![proto::HttpHeader {
            key: "accept".into(),
            value: "text/plain".into(),
        }],
        body: vec![],
    };

    let mut http_state = HttpState::new();
    let result = http_state.request("123".into(), request, connection).await;
    assert!(result.is_ok(), "Request should not crash");

    // Verify response contains valid protobuf message
    let written = handle.take_written().await;
    let response = proto::ServerToClientMessage::decode(&written[4..]).unwrap();

    assert_eq!(response.origin.unwrap().id, "123");
    assert!(response.payload.is_some(), "Response should have payload");
}

#[tokio::test]
async fn test_invalid_http_request() {
    let (stream, mut handle) = MockTcpStream::new();
    let connection = SinkConnection::new(stream);

    let request = proto::HttpProxyRequest {
        url: "".into(),
        method: "GET".into(),
        headers: vec![],
        body: vec![],
    };

    let mut http_state = HttpState::new();
    let result = http_state.request("123".into(), request, connection).await;
    assert!(result.is_ok(), "Request should not crash");

    // Verify response contains valid protobuf message
    let written = handle.take_written().await;
    let response = proto::ServerToClientMessage::decode(&written[4..]).unwrap();

    assert_eq!(response.origin.unwrap().id, "123");
    assert!(response.payload.is_some(), "Response should have payload");

    match response.payload.unwrap() {
        proto::server_to_client_message::Payload::HttpError(err) => {
            assert_eq!(err.error_code, 500);
        }
        _ => panic!("Expected error message"),
    }
}
