use crate::{
    connection::SinkConnection,
    error::{Error, Result},
    proto::{
        server_to_client_message, HttpHeader, HttpRequestError, HttpResponseBodyChunk,
        HttpResponseComplete, HttpResponseHeaders, RequestOrigin, ServerToClientMessage,
    },
    state::util::header_map,
};
use futures::StreamExt;
use http::Method;
use log::{error, info};
use reqwest::Client;
use tokio::io::{AsyncRead, AsyncWrite};

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

    pub async fn request<T>(
        &mut self,
        request_id: String,
        request: HttpProxyRequest,
        connection: SinkConnection<T>,
    ) -> Result<()>
    where
        T: AsyncRead + AsyncWrite + Send + 'static,
    {
        let headers = header_map(request.headers)?;

        let method = match Method::from_bytes(request.method.as_bytes()) {
            Ok(method) => method,
            Err(err) => Err(<http::method::InvalidMethod as Into<Error>>::into(err))?,
        };

        info!("Making HTTP request to {}", request.url);
        let response = match self
            .client
            .request(method, &request.url)
            .headers(headers)
            .body(request.body)
            .send()
            .await
        {
            Ok(res) => {
                info!("Received HTTP response with status: {}", res.status());
                res
            }
            Err(e) => {
                error!("HTTP request failed: {}", e);
                let err_msg = ServerToClientMessage {
                    origin: Some(RequestOrigin { id: request_id }),
                    payload: Some(server_to_client_message::Payload::HttpError(
                        HttpRequestError {
                            error_message: e.to_string(),
                            error_code: 500,
                        },
                    )),
                };
                info!("Sending error response");
                connection.write(err_msg).await?;
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
        connection.write(headers_msg).await?;

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
            connection.write(chunk_msg).await?;
        }

        // Send completion
        let complete_msg = ServerToClientMessage {
            origin: Some(RequestOrigin { id: request_id }),
            payload: Some(server_to_client_message::Payload::HttpResponseComplete(
                HttpResponseComplete {},
            )),
        };
        connection.write(complete_msg).await?;

        Ok(())
    }
}
