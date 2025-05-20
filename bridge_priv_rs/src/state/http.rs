use crate::{
    connection::SinkConnection,
    error::{Error, Result},
    proto::{
        server_to_client_message, HttpHeader, HttpRequestError, HttpResponseBodyChunk,
        HttpResponseComplete, HttpResponseHeaders, RequestOrigin, ServerToClientMessage,
    },
};
use bytes::Bytes;
use futures::{SinkExt, StreamExt};
use http::{HeaderMap, HeaderName, HeaderValue, Method};
use prost::Message;
use reqwest::Client;
use std::io::ErrorKind;
use tokio::io;

use crate::proto::HttpProxyRequest;

pub struct HttpState {
    client: Client,
}

impl HttpState {
    pub fn new() -> Self {
        Self {
            client: Client::new(),
        }
    }

    pub async fn request(
        &mut self,
        request_id: String,
        request: HttpProxyRequest,
        connection: SinkConnection,
    ) -> Result<()> {
        // Convert protobuf headers to HeaderMap
        let mut headers = HeaderMap::new();
        for h in request.headers {
            headers.insert(
                HeaderName::from_bytes(h.key.as_bytes())
                    .map_err(|e| io::Error::new(ErrorKind::InvalidData, e))?,
                HeaderValue::from_str(&h.value)
                    .map_err(|e| io::Error::new(ErrorKind::InvalidData, e))?,
            );
        }

        let method = match Method::from_bytes(request.method.as_bytes()) {
            Ok(method) => method,
            Err(err) => Err(<http::method::InvalidMethod as Into<Error>>::into(err))?,
        };

        let response = match self
            .client
            .request(method, &request.url)
            .headers(headers)
            .body(request.body)
            .send()
            .await
        {
            Ok(res) => res,
            Err(e) => {
                let err_msg = ServerToClientMessage {
                    origin: Some(RequestOrigin { id: request_id }),
                    payload: Some(server_to_client_message::Payload::HttpError(
                        HttpRequestError {
                            error_message: e.to_string(),
                            error_code: 500,
                        },
                    )),
                };
                connection
                    .lock()
                    .await
                    .send(Bytes::from(err_msg.encode_to_vec()))
                    .await?;
                return Ok(());
            }
        };

        // Send headers
        let headers_msg = ServerToClientMessage {
            origin: Some(RequestOrigin {
                id: request_id.clone(),
            }),
            payload: Some(server_to_client_message::Payload::HttpHeaders(
                HttpResponseHeaders {
                    status_code: response.status().as_u16() as i32,
                    headers: response
                        .headers()
                        .iter()
                        .map(|(k, v)| HttpHeader {
                            key: k.to_string(),
                            value: v.to_str().unwrap_or("").to_owned(),
                        })
                        .collect(),
                },
            )),
        };
        connection
            .lock()
            .await
            .send(Bytes::from(headers_msg.encode_to_vec()))
            .await?;

        // Stream body chunks
        let mut stream = response.bytes_stream();
        while let Some(chunk) = stream.next().await {
            let chunk = chunk?;
            let chunk_msg = ServerToClientMessage {
                origin: Some(RequestOrigin {
                    id: request_id.clone(),
                }),
                payload: Some(server_to_client_message::Payload::HttpBodyChunk(
                    HttpResponseBodyChunk {
                        chunk: chunk.to_vec(),
                    },
                )),
            };
            connection
                .lock()
                .await
                .send(Bytes::from(chunk_msg.encode_to_vec()))
                .await?;
        }

        // Send completion
        let complete_msg = ServerToClientMessage {
            origin: Some(RequestOrigin { id: request_id }),
            payload: Some(server_to_client_message::Payload::HttpResponseComplete(
                HttpResponseComplete {},
            )),
        };
        connection
            .lock()
            .await
            .send(Bytes::from(complete_msg.encode_to_vec()))
            .await?;

        Ok(())
    }
}
