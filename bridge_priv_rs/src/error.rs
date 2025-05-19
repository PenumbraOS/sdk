use std::io;

use thiserror::Error;

#[derive(Error, Debug)]
pub enum Error {
    #[error("IO error {0}")]
    IO(#[from] io::Error),
    #[error("HTTP error {0}")]
    ReqwestError(#[from] reqwest::Error),
    #[error("HTTP error {0}")]
    HttpError(#[from] http::method::InvalidMethod),
    #[error("Protocol decode error {0}")]
    ProtocolDecodeError(#[from] prost::DecodeError),

    #[error("Unknown error {0}")]
    Unknown(String),
}

pub type Result<T> = std::result::Result<T, Error>;
