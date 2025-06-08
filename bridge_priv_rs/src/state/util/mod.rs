use std::io::{self, ErrorKind};

use http::{HeaderMap, HeaderName, HeaderValue};

use crate::{error::Result, proto::HttpHeader};

pub fn header_map(headers: Vec<HttpHeader>) -> Result<HeaderMap> {
    let mut header_map = HeaderMap::new();
    for header in headers {
        header_map.insert(
            HeaderName::from_bytes(header.key.as_bytes())
                .map_err(|e| io::Error::new(ErrorKind::InvalidData, e))?,
            HeaderValue::from_str(&header.value)
                .map_err(|e| io::Error::new(ErrorKind::InvalidData, e))?,
        );
    }

    Ok(header_map)
}
