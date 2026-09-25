;; Implements example:tuples/tuple-types, a world whose only types are tuples.
;;
;; A tuple of two u32 does not fit the one flat result the ABI allows, so the
;; imported describe is handed a return area to write its pair into, while the
;; exported shift hands back a pointer to the fields of its own result.
;;
;; shift traps unless it is given (7, 11) and 5, and again unless the host
;; answers (14, 22), so a test passes only on values that actually crossed.
(module
  (import "example:tuples/points" "describe" (func $describe (param i32 i32 i32 i32)))
  (memory (export "memory") 1)
  (data (i32.const 100) "origin")
  (data (i32.const 120) "shifted")
  (global $heap (mut i32) (i32.const 1024))

  (func (export "cabi_realloc") (param i32 i32 i32 i32) (result i32)
    (local $p i32)
    global.get $heap
    local.set $p
    global.get $heap
    local.get 3
    i32.add
    global.set $heap
    local.get $p)

  (func (export "shift") (param $x i32) (param $y i32) (param $by i32) (result i32)
    local.get $x
    i32.const 7
    i32.ne
    if
      unreachable
    end
    local.get $y
    i32.const 11
    i32.ne
    if
      unreachable
    end
    local.get $by
    i32.const 5
    i32.ne
    if
      unreachable
    end

    ;; describe(("origin", 3)), whose pair of u32 lands at 200.
    i32.const 100
    i32.const 6
    i32.const 3
    i32.const 200
    call $describe

    i32.const 200
    i32.load
    i32.const 14
    i32.ne
    if
      unreachable
    end
    i32.const 200
    i32.load offset=4
    i32.const 22
    i32.ne
    if
      unreachable
    end

    ;; ("shifted", 18), laid out at 216 as a pointer, a length and a u32.
    i32.const 216
    i32.const 120
    i32.store
    i32.const 216
    i32.const 7
    i32.store offset=4
    i32.const 216
    i32.const 18
    i32.store offset=8
    i32.const 216))
