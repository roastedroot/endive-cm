;; Implements example:result-types/result-types, a world written for these tests
;; rather than taken from a bindgen! example, since none of them uses a result.
;;
;; run hands its string to the imported parse and traps unless what comes back is
;; one of the two outcomes the tests say the embedder produces, so a passing test
;; shows the ok payload and the error payload both crossing. check traps on any
;; value but the two it is given.
;;
;; A result of more than one flat value travels through memory, so run, check and
;; count hand back a pointer, while ping, whose result is a bare discriminant,
;; returns it directly.
(module
  (import "example:result-types/parsing" "parse" (func $parse (param i32 i32 i32)))
  (memory (export "memory") 1)
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

  ;; result<u32, run-error> holds a one byte discriminant and then its payload at
  ;; offset 4, where the widest case, the u32, aligns.
  (func (export "example:result-types/running#run") (param $ptr i32) (param $len i32) (result i32)
    local.get $ptr
    local.get $len
    i32.const 256
    call $parse

    i32.const 256
    i32.load8_u
    if (result i32)
      ;; The embedder threw, and the payload must be parse-error.overflow.
      i32.const 256
      i32.load8_u offset=4
      i32.const 1
      i32.ne
      if
        unreachable
      end
      i32.const 300
      i32.const 1
      i32.store8
      i32.const 300
      i32.const 0
      i32.store8 offset=4
      i32.const 300
    else
      ;; The embedder returned, and the payload must be 21.
      i32.const 256
      i32.load offset=4
      i32.const 21
      i32.ne
      if
        unreachable
      end
      i32.const 300
      i32.const 0
      i32.store8
      i32.const 300
      i32.const 22
      i32.store offset=4
      i32.const 300
    end)

  ;; result<_, run-error> holds a one byte discriminant and a one byte payload.
  (func (export "example:result-types/running#check") (param $value i32) (result i32)
    local.get $value
    i32.const 7
    i32.eq
    if (result i32)
      i32.const 320
      i32.const 0
      i32.store8
      i32.const 320
    else
      local.get $value
      i32.const 8
      i32.ne
      if
        unreachable
      end
      i32.const 320
      i32.const 1
      i32.store8
      i32.const 320
      i32.const 0
      i32.store8 offset=1
      i32.const 320
    end)

  ;; result<u32> has an error case carrying nothing at all.
  (func (export "example:result-types/running#count") (param $fail i32) (result i32)
    local.get $fail
    if (result i32)
      i32.const 340
      i32.const 1
      i32.store8
      i32.const 340
    else
      i32.const 340
      i32.const 0
      i32.store8
      i32.const 340
      i32.const 99
      i32.store offset=4
      i32.const 340
    end)

  ;; Neither case of a bare result carries a payload, so the discriminant is the
  ;; whole of what is returned.
  (func (export "example:result-types/running#ping") (param $fail i32) (result i32)
    local.get $fail))
