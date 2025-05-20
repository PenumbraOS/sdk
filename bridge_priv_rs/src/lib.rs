pub mod proto {
    include!(concat!(env!("OUT_DIR"), "/com.penumbraos.ipc.proxy.rs"));
}

pub mod connection;
pub mod error;
pub mod state;
