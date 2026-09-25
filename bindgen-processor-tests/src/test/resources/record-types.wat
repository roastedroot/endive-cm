;; Implements example:records/record-types, a world built for this test rather
;; than taken from a bindgen! example, because none of those declares a record.
;;
;; A record does not fit the one flat result the ABI allows, so resident is
;; handed a return area and check reads the person out of it field by field.
;; The layout is the Canonical ABI's: name at 0, active at 8, id at 16, initial
;; at 24, tags at 28 and home at 36, giving a record of 48 bytes aligned to 8.
;;
;; check traps unless every field holds what the test says the host handed over,
;; and widen traps unless it is handed the span the test passes, so neither can
;; pass on bindings whose values never arrived.
(module
  (import "example:records/types" "resident" (func $resident (param i32)))
  (memory (export "memory") 1)
  (global $heap (mut i32) (i32.const 1024))

  ;; A record's fields are allocated at different alignments, so the bump
  ;; pointer is rounded up to the one asked for rather than handed out as it is.
  (func (export "cabi_realloc") (param i32 i32 i32 i32) (result i32)
    (local $p i32)
    global.get $heap
    local.get 2
    i32.add
    i32.const 1
    i32.sub
    local.get 2
    i32.const 1
    i32.sub
    i32.const -1
    i32.xor
    i32.and
    local.tee $p
    local.get 3
    i32.add
    global.set $heap
    local.get $p)

  (func $expect (param $actual i32) (param $wanted i32)
    local.get $actual
    local.get $wanted
    i32.ne
    if
      unreachable
    end)

  ;; The byte at $at of the string whose (pointer, length) pair sits at $pair.
  (func $byte (param $pair i32) (param $at i32) (result i32)
    local.get $pair
    i32.load
    local.get $at
    i32.add
    i32.load8_u)

  (func (export "check") (result i32)
    (local $tags i32)
    i32.const 0
    call $resident

    ;; name is "ada"
    i32.const 4
    i32.load
    i32.const 3
    call $expect
    i32.const 0
    i32.const 0
    call $byte
    i32.const 0x61
    call $expect
    i32.const 0
    i32.const 1
    call $byte
    i32.const 0x64
    call $expect
    i32.const 0
    i32.const 2
    call $byte
    i32.const 0x61
    call $expect

    ;; active is true
    i32.const 8
    i32.load8_u
    i32.const 1
    call $expect

    ;; id is beyond what a signed long holds
    i32.const 16
    i64.load
    i64.const 0x8000000000000009
    i64.ne
    if
      unreachable
    end

    ;; initial is U+1F980, which no single Java char holds
    i32.const 24
    i32.load
    i32.const 0x1F980
    call $expect

    ;; tags is ["hot", "new"]
    i32.const 32
    i32.load
    i32.const 2
    call $expect
    i32.const 28
    i32.load
    local.set $tags
    local.get $tags
    i32.const 0
    call $byte
    i32.const 0x68
    call $expect
    local.get $tags
    i32.load offset=4
    i32.const 3
    call $expect
    local.get $tags
    i32.const 8
    i32.add
    i32.const 0
    call $byte
    i32.const 0x6E
    call $expect
    local.get $tags
    i32.load offset=12
    i32.const 3
    call $expect

    ;; home is point(7, 11)
    i32.const 36
    i32.load
    i32.const 7
    call $expect
    i32.const 40
    i32.load
    i32.const 11
    call $expect

    ;; home.x + home.y + the number of tags
    i32.const 36
    i32.load
    i32.const 40
    i32.load
    i32.add
    i32.const 32
    i32.load
    i32.add)

  ;; An exported function returning a record hands back a pointer to it rather
  ;; than being given somewhere to write it.
  (func (export "example:records/shapes#widen")
        (param $start i32) (param $end i32) (param $by i32) (result i32)
    local.get $start
    i32.const 2
    call $expect
    local.get $end
    i32.const 5
    call $expect
    local.get $by
    i32.const 3
    call $expect
    i32.const 512
    local.get $start
    i32.store
    i32.const 512
    local.get $end
    local.get $by
    i32.add
    i32.store offset=4
    i32.const 512))
