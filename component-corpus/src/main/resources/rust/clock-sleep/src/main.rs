use std::thread;
use std::time::{Duration, Instant};

fn main() {
    let pause = Duration::from_millis(10);
    let start = Instant::now();
    thread::sleep(pause);
    if start.elapsed() >= pause {
        println!("monotonic clock advanced");
    } else {
        println!("monotonic clock advanced less than {pause:?}");
    }
}
