use std::cell::Cell;
use std::sync::atomic::{AtomicU32, Ordering};

use exports::endive::corpus::types::{Color, Guest, GuestCounter, Permissions, Point, Shape};

wit_bindgen::generate!({
    path: "wit",
    world: "library",
});

static LIVE_COUNTERS: AtomicU32 = AtomicU32::new(0);

struct Library;

impl Guest for Library {
    type Counter = Counter;

    fn live_counters() -> u32 {
        LIVE_COUNTERS.load(Ordering::Relaxed)
    }

    fn translate(p: Point, dx: i32, dy: i32) -> Point {
        Point {
            x: p.x + dx,
            y: p.y + dy,
        }
    }

    fn area(s: Shape) -> u64 {
        area_of(&s)
    }

    fn largest(shapes: Vec<Shape>) -> Option<Shape> {
        shapes.into_iter().reduce(|largest, shape| {
            if area_of(&shape) > area_of(&largest) {
                shape
            } else {
                largest
            }
        })
    }

    fn next_color(c: Color) -> Color {
        match c {
            Color::Red => Color::Green,
            Color::Green => Color::Blue,
            Color::Blue => Color::Red,
        }
    }

    fn toggle(current: Permissions, mask: Permissions) -> Permissions {
        current ^ mask
    }

    fn first_word(text: String) -> Option<String> {
        text.split_whitespace().next().map(String::from)
    }

    fn checked_divide(dividend: i32, divisor: i32) -> Result<i32, String> {
        if divisor == 0 {
            return Err(String::from("division by zero"));
        }
        dividend
            .checked_div(divisor)
            .ok_or_else(|| String::from("overflow"))
    }

    fn sum(values: Vec<i64>) -> i64 {
        values.iter().sum()
    }

    fn reverse(mut words: Vec<String>) -> Vec<String> {
        words.reverse();
        words
    }

    fn greet(name: String) -> String {
        format!("Hello, {name}!")
    }

    fn swap(pair: (String, u32)) -> (u32, String) {
        (pair.1, pair.0)
    }
}

fn area_of(s: &Shape) -> u64 {
    match s {
        Shape::Square(side) => u64::from(*side) * u64::from(*side),
        Shape::Rectangle(corner) => {
            u64::from(corner.x.unsigned_abs()) * u64::from(corner.y.unsigned_abs())
        }
        Shape::Dot => 0,
    }
}

struct Counter {
    value: Cell<u32>,
}

impl GuestCounter for Counter {
    fn new(initial: u32) -> Self {
        LIVE_COUNTERS.fetch_add(1, Ordering::Relaxed);
        Counter {
            value: Cell::new(initial),
        }
    }

    fn increment(&self, by: u32) -> u32 {
        let value = self.value.get() + by;
        self.value.set(value);
        value
    }
}

impl Drop for Counter {
    fn drop(&mut self) {
        LIVE_COUNTERS.fetch_sub(1, Ordering::Relaxed);
    }
}

export!(Library);
