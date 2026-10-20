use std::fs;

fn main() {
    let input = fs::read_to_string("input.txt").expect("read input.txt");
    fs::write("output.txt", input.to_uppercase()).expect("write output.txt");
    let output = fs::read_to_string("output.txt").expect("read output.txt");
    print!("{output}");
    fs::remove_file("output.txt").expect("remove output.txt");
}
