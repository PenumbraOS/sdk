use bytes::{Buf, BytesMut};
use prost::Message;
use std::pin::Pin;
use std::task::{Context, Poll};
use tokio::io::{AsyncRead, AsyncWrite, ReadBuf};
use tokio::sync::mpsc;
use tokio_util::codec::{Framed, LengthDelimitedCodec};

/// Mock TCP stream for testing with channel-based communication
pub struct MockTcpStream {
    read_rx: mpsc::Receiver<BytesMut>,
    write_tx: mpsc::Sender<BytesMut>,
    current_read: BytesMut,
}

/// Handle for interacting with the mock stream from tests
pub struct MockTcpStreamHandle {
    pub read_tx: mpsc::Sender<BytesMut>,
    pub write_rx: mpsc::Receiver<BytesMut>,
}

impl MockTcpStream {
    pub fn new() -> (Self, MockTcpStreamHandle) {
        let (read_tx, read_rx) = mpsc::channel(10);
        let (write_tx, write_rx) = mpsc::channel(10);

        let stream = Self {
            read_rx,
            write_tx,
            current_read: BytesMut::new(),
        };

        let handle = MockTcpStreamHandle { read_tx, write_rx };

        (stream, handle)
    }

    pub fn framed(self) -> Framed<Self, LengthDelimitedCodec> {
        Framed::new(self, LengthDelimitedCodec::new())
    }
}

impl MockTcpStreamHandle {
    pub async fn push_bytes(&mut self, bytes: &[u8]) {
        self.read_tx.send(BytesMut::from(bytes)).await.unwrap();
    }

    pub async fn take_written(&mut self) -> BytesMut {
        self.write_rx.recv().await.unwrap()
    }
}

impl AsyncRead for MockTcpStream {
    fn poll_read(
        mut self: Pin<&mut Self>,
        cx: &mut Context<'_>,
        buf: &mut ReadBuf<'_>,
    ) -> Poll<std::io::Result<()>> {
        // First try to read from current buffer
        if !self.current_read.is_empty() {
            let len = std::cmp::min(buf.remaining(), self.current_read.len());
            buf.put_slice(&self.current_read[..len]);
            self.current_read.advance(len);
            return Poll::Ready(Ok(()));
        }

        // If current buffer empty, try to get more from channel
        match self.read_rx.poll_recv(cx) {
            Poll::Ready(Some(bytes)) => {
                self.current_read = bytes;
                let len = std::cmp::min(buf.remaining(), self.current_read.len());
                if len > 0 {
                    buf.put_slice(&self.current_read[..len]);
                    self.current_read.advance(len);
                }
                Poll::Ready(Ok(()))
            }
            Poll::Ready(None) => Poll::Ready(Ok(())), // Channel closed
            Poll::Pending => Poll::Pending,
        }
    }
}

impl AsyncWrite for MockTcpStream {
    fn poll_write(
        self: Pin<&mut Self>,
        _cx: &mut Context<'_>,
        buf: &[u8],
    ) -> Poll<std::io::Result<usize>> {
        match self.write_tx.try_send(BytesMut::from(buf)) {
            Ok(_) => Poll::Ready(Ok(buf.len())),
            Err(e) => match e {
                tokio::sync::mpsc::error::TrySendError::Full(_) => Poll::Pending,
                tokio::sync::mpsc::error::TrySendError::Closed(_) => Poll::Ready(Err(
                    std::io::Error::new(std::io::ErrorKind::BrokenPipe, "channel closed"),
                )),
            },
        }
    }

    fn poll_flush(self: Pin<&mut Self>, _cx: &mut Context<'_>) -> Poll<std::io::Result<()>> {
        Poll::Ready(Ok(()))
    }

    fn poll_shutdown(self: Pin<&mut Self>, _cx: &mut Context<'_>) -> Poll<std::io::Result<()>> {
        Poll::Ready(Ok(()))
    }
}

/// Helper to send a protobuf message through a framed stream
pub async fn send_proto<M>(handle: &mut MockTcpStreamHandle, msg: M)
where
    M: Message,
{
    let bytes = BytesMut::from(msg.encode_to_vec().as_slice());
    handle.push_bytes(&(bytes.len() as u32).to_le_bytes()).await;
    handle.push_bytes(&bytes).await
}
