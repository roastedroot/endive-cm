fn main() {
    for name in ["GREETING", "TARGET", "UNSET"] {
        match std::env::var(name) {
            Ok(value) => println!("{name}={value}"),
            Err(_) => println!("{name} is not set"),
        }
    }
}
