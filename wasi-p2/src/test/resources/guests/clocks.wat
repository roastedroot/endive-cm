;; Implements endive:wasi-test/clocks, which exports run alongside every import
;; the host world provides.
;;
;; wall-clock now is handed a return area for its datetime, a record whose
;; seconds is a u64 at 0 and whose nanoseconds is a u32 at 8. poll is handed the
;; pollables as a pointer and a length, one i32 handle each, and a return area
;; for its list<u32>, whose pointer is at 0 and whose length is at 4. The host
;; allocates that list through cabi_realloc.
;;
;; run traps unless the wall clock reads 1700000000 seconds and 5 nanoseconds,
;; polling a 10 s and a 20 ms subscription answers only the 20 ms one, the 10 s
;; one is not ready afterwards, and blocking on a 5 ms subscription leaves it
;; ready. It then returns the monotonic time elapsed since it started.
(module
  (import "wasi:clocks/monotonic-clock@0.2.12" "now" (func $now (result i64)))
  (import "wasi:clocks/monotonic-clock@0.2.12" "subscribe-duration"
    (func $subscribe-duration (param i64) (result i32)))
  (import "wasi:clocks/wall-clock@0.2.12" "now" (func $wall-now (param i32)))
  (import "wasi:io/poll@0.2.12" "poll" (func $poll (param i32 i32 i32)))
  (import "wasi:io/poll@0.2.12" "[method]pollable.ready" (func $ready (param i32) (result i32)))
  (import "wasi:io/poll@0.2.12" "[method]pollable.block" (func $block (param i32)))
  (import "wasi:io/poll@0.2.12" "[resource-drop]pollable" (func $drop (param i32)))
  (memory (export "memory") 1)
  (global $heap (mut i32) (i32.const 1024))

  (func (export "cabi_realloc") (param i32 i32 i32 i32) (result i32)
    (local $p i32)
    global.get $heap
    local.set $p
    global.get $heap
    local.get 3
    i32.const 7
    i32.add
    i32.const -8
    i32.and
    i32.add
    global.set $heap
    local.get $p)

  (func (export "run") (result i64)
    (local $start i64)
    (local $long i32)
    (local $short i32)
    (local $timer i32)
    call $now
    local.set $start

    ;; wall-clock now() -> datetime { seconds: 1700000000, nanoseconds: 5 }
    i32.const 400
    call $wall-now
    i32.const 400
    i64.load
    i64.const 1700000000
    i64.ne
    if
      unreachable
    end
    i32.const 400
    i32.load offset=8
    i32.const 5
    i32.ne
    if
      unreachable
    end

    ;; long = subscribe-duration(10 s), short = subscribe-duration(20 ms)
    i64.const 10000000000
    call $subscribe-duration
    local.set $long
    i64.const 20000000
    call $subscribe-duration
    local.set $short

    ;; poll([long, short]) -> [1]
    i32.const 200
    local.get $long
    i32.store
    i32.const 200
    local.get $short
    i32.store offset=4
    i32.const 200
    i32.const 2
    i32.const 300
    call $poll
    i32.const 300
    i32.load offset=4
    i32.const 1
    i32.ne
    if
      unreachable
    end
    i32.const 300
    i32.load
    i32.load
    i32.const 1
    i32.ne
    if
      unreachable
    end

    ;; long.ready() -> false
    local.get $long
    call $ready
    if
      unreachable
    end
    local.get $long
    call $drop
    local.get $short
    call $drop

    ;; subscribe-duration(5 ms).block(), after which it is ready
    i64.const 5000000
    call $subscribe-duration
    local.tee $timer
    call $block
    local.get $timer
    call $ready
    i32.eqz
    if
      unreachable
    end
    local.get $timer
    call $drop

    call $now
    local.get $start
    i64.sub))
