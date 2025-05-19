use bytes::Bytes;
use error::{Error, Result};
use futures::sink::SinkExt;
use futures_util::StreamExt;
use prost::Message;
use reqwest::{
    header::{HeaderMap, HeaderName, HeaderValue},
    Client, Method,
};
use std::{
    io::{self, ErrorKind},
    sync::Arc,
};
use tokio::net::{TcpListener, TcpStream};
use tokio_util::codec::{Framed, LengthDelimitedCodec};

mod proto {
    include!(concat!(env!("OUT_DIR"), "/com.penumbraos.ipc.proxy.rs"));
}

mod error;

#[derive(Debug)]
struct AppState {
    http_client: Client,
}

impl AppState {
    fn new() -> Self {
        Self {
            http_client: Client::new(),
        }
    }
}

async fn handle_connection(stream: TcpStream, state: Arc<AppState>) -> Result<()> {
    let mut framed = Framed::new(stream, LengthDelimitedCodec::new());

    while let Some(msg) = framed.next().await {
        let bytes = msg?;
        let client_msg = proto::ClientToServerMessage::decode(bytes)?;

        if let Some(proto::client_to_server_message::Payload::HttpRequest(req)) = client_msg.payload
        {
            handle_http_request(
                state.clone(),
                client_msg.origin.unwrap().id,
                req,
                &mut framed,
            )
            .await?;
        }
    }

    Ok(())
}

async fn handle_http_request(
    state: Arc<AppState>,
    request_id: i32,
    req: proto::HttpProxyRequest,
    framed: &mut Framed<TcpStream, LengthDelimitedCodec>,
) -> Result<()> {
    // Convert protobuf headers to HeaderMap
    let mut headers = HeaderMap::new();
    for h in req.headers {
        headers.insert(
            HeaderName::from_bytes(h.key.as_bytes())
                .map_err(|e| io::Error::new(ErrorKind::InvalidData, e))?,
            HeaderValue::from_str(&h.value)
                .map_err(|e| io::Error::new(ErrorKind::InvalidData, e))?,
        );
    }

    let method = match Method::from_bytes(req.method.as_bytes()) {
        Ok(method) => method,
        Err(err) => Err(<http::method::InvalidMethod as Into<Error>>::into(err))?,
    };

    let response = match state
        .http_client
        .request(method, &req.url)
        .headers(headers)
        .body(req.body)
        .send()
        .await
    {
        Ok(res) => res,
        Err(e) => {
            let err_msg = proto::ServerToClientMessage {
                origin: Some(proto::RequestOrigin { id: request_id }),
                payload: Some(proto::server_to_client_message::Payload::HttpError(
                    proto::HttpRequestError {
                        error_message: e.to_string(),
                        error_code: 500,
                    },
                )),
            };
            framed.send(Bytes::from(err_msg.encode_to_vec())).await?;
            return Ok(());
        }
    };

    // Send headers
    let headers_msg = proto::ServerToClientMessage {
        origin: Some(proto::RequestOrigin { id: request_id }),
        payload: Some(proto::server_to_client_message::Payload::HttpHeaders(
            proto::HttpResponseHeaders {
                status_code: response.status().as_u16() as i32,
                headers: response
                    .headers()
                    .iter()
                    .map(|(k, v)| proto::HttpHeader {
                        key: k.to_string(),
                        value: v.to_str().unwrap_or("").to_owned(),
                    })
                    .collect(),
            },
        )),
    };
    framed
        .send(Bytes::from(headers_msg.encode_to_vec()))
        .await?;

    // Stream body chunks
    let mut stream = response.bytes_stream();
    while let Some(chunk) = stream.next().await {
        let chunk = chunk?;
        let chunk_msg = proto::ServerToClientMessage {
            origin: Some(proto::RequestOrigin { id: request_id }),
            payload: Some(proto::server_to_client_message::Payload::HttpBodyChunk(
                proto::HttpResponseBodyChunk {
                    chunk: chunk.to_vec(),
                },
            )),
        };
        framed.send(Bytes::from(chunk_msg.encode_to_vec())).await?;
    }

    // Send completion
    let complete_msg = proto::ServerToClientMessage {
        origin: Some(proto::RequestOrigin { id: request_id }),
        payload: Some(
            proto::server_to_client_message::Payload::HttpResponseComplete(
                proto::HttpResponseComplete {},
            ),
        ),
    };
    framed
        .send(Bytes::from(complete_msg.encode_to_vec()))
        .await?;

    Ok(())
}

#[tokio::main]
async fn main() -> io::Result<()> {
    let state = Arc::new(AppState::new());
    let listener = TcpListener::bind("127.0.0.1:8080").await?;
    println!("Server listening on {}", listener.local_addr()?);

    loop {
        let (stream, _) = listener.accept().await?;
        let state = state.clone();

        tokio::spawn(async move {
            if let Err(e) = handle_connection(stream, state).await {
                eprintln!("Connection error: {}", e);
            }
        });
    }
}
