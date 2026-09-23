;; Implements example:variant-types/variant-types, a world built for this test
;; rather than taken from a bindgen example, since none of them declares a
;; variant.
;;
;; Both variants mix a payload-free case with payload-carrying ones. A variant
;; flattens to a discriminant plus the join of its cases, which is three i32s
;; here, so a result goes through memory. An import is handed a return area and
;; an export hands back a pointer, which is why the two directions differ.
;;
;; In memory a variant is a one byte discriminant, then its payload aligned to
;; four, so a case payload sits at offset four.
;;
;; Every function traps unless it is handed exactly what the test says it was
;; handed, so a value that never arrived cannot pass.
(module
  (import "example:variant-types/commands" "handle"
    (func $handle (param i32 i32 i32 i32)))
  (memory (export "memory") 1)
  (data (i32.const 100) "go")
  (data (i32.const 140) "quiet")

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

  ;; Calls the host twice, once with a payload and once without, and traps
  ;; unless each reply is the case the test says the host returns.
  (func (export "example:variant-types/replies#run")
    ;; handle(speak("go")), whose reply must be stop.
    i32.const 2
    i32.const 100
    i32.const 2
    i32.const 256
    call $handle

    i32.const 256
    i32.load8_u
    i32.const 0
    i32.ne
    if
      unreachable
    end

    ;; handle(stop), whose reply must be jump(7).
    i32.const 0
    i32.const 0
    i32.const 0
    i32.const 256
    call $handle

    i32.const 256
    i32.load8_u
    i32.const 1
    i32.ne
    if
      unreachable
    end

    i32.const 260
    i32.load
    i32.const 7
    i32.ne
    if
      unreachable
    end)

  ;; Answers silence with text("quiet") and text("hello") with silence, so each
  ;; direction carries a payload one way and none the other.
  (func (export "example:variant-types/replies#echo")
    (param $disc i32) (param $ptr i32) (param $len i32) (result i32)
    local.get $disc
    i32.const 1
    i32.eq
    if
      ;; text(x), whose payload must be "hello".
      local.get $len
      i32.const 5
      i32.ne
      if
        unreachable
      end

      local.get $ptr
      i32.load8_u
      i32.const 0x68
      i32.ne
      if
        unreachable
      end

      i32.const 288
      i32.const 0
      i32.store8
      i32.const 288
      return
    end

    ;; silence, answered with text("quiet").
    i32.const 288
    i32.const 1
    i32.store8
    i32.const 292
    i32.const 140
    i32.store
    i32.const 296
    i32.const 5
    i32.store
    i32.const 288))
