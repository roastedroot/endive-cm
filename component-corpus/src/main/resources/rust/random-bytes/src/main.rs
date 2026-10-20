use wasip2::random::{insecure, random};

fn main() {
    let bytes = random::get_random_bytes(16);
    println!("random bytes: {}", bytes.len());
    let bytes = insecure::get_insecure_random_bytes(8);
    println!("insecure random bytes: {}", bytes.len());
}
