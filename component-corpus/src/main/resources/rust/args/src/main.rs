fn main() {
    for arg in std::env::args().skip(1) {
        println!("{arg}");
    }
}
