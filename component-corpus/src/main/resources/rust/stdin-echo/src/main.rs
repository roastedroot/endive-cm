use std::io::{self, Read, Write};

fn main() {
    let mut input = Vec::new();
    io::stdin().read_to_end(&mut input).expect("read stdin");
    io::stdout().write_all(&input).expect("write stdout");
    eprintln!("echoed {} bytes", input.len());
}
