use std::path::PathBuf;

fn main() -> Result<(), Box<dyn std::error::Error>> {
    let out_dir = PathBuf::from(std::env::var("OUT_DIR")?);
    let proto_file = "proto/ipc.proto";

    prost_build::Config::new()
        .out_dir(out_dir)
        .compile_protos(&[proto_file], &["proto/"])?;

    Ok(())
}
