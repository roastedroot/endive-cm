;; Implements example:option-types/option-types.
;;
;; An option<u32> flattens to a discriminant and a payload, where the
;; discriminant is 0 for none and 1 for some, and occupies eight bytes in
;; memory, the payload starting at offset 4.
;;
;; An exported function returning one hands back a pointer to where it stored
;; it, while an imported one is given somewhere to write it.
;;
;; run echoes its argument through the host and traps unless a none comes back
;; as a none and a some comes back carrying one more. It answers some(7) for a
;; none and none for a some, so both cases cross in both directions.
(module
  (import "example:option-types/maybe" "echo" (func $echo (param i32 i32 i32)))
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

  (func $option (param $at i32) (param $disc i32) (param $payload i32) (result i32)
    local.get $at
    local.get $disc
    i32.store8
    local.get $at
    local.get $payload
    i32.store offset=4
    local.get $at)

  (func (export "run") (param $disc i32) (param $payload i32) (result i32)
    local.get $disc
    local.get $payload
    i32.const 256
    call $echo

    local.get $disc
    if (result i32)
      i32.const 256
      i32.load8_u
      i32.const 1
      i32.ne
      if
        unreachable
      end
      i32.const 256
      i32.load offset=4
      local.get $payload
      i32.const 1
      i32.add
      i32.ne
      if
        unreachable
      end
      i32.const 512
      i32.const 0
      i32.const 0
      call $option
    else
      i32.const 256
      i32.load8_u
      if
        unreachable
      end
      i32.const 512
      i32.const 1
      i32.const 7
      call $option
    end))
