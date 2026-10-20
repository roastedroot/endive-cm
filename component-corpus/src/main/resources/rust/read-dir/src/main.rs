fn main() {
    let mut names: Vec<String> = std::fs::read_dir("listing")
        .expect("read listing")
        .map(|entry| {
            let entry = entry.expect("read entry");
            let mut name = entry.file_name().into_string().expect("UTF-8 name");
            if entry.file_type().expect("read file type").is_dir() {
                name.push('/');
            }
            name
        })
        .collect();
    names.sort();
    for name in names {
        println!("{name}");
    }
}
