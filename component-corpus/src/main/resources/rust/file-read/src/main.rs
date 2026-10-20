fn main() {
    let text = std::fs::read_to_string("greeting.txt").expect("read greeting.txt");
    print!("{text}");
}
