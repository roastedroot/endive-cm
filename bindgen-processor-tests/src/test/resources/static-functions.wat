;; Implements example:static-functions/static-functions, a world whose resources
;; carry static functions in both directions. A static has no receiver, so one
;; returning a handle mints a resource and one returning a plain value does not.
;;
;; run drives the imported side. It opens a host counter at 7, reads the value
;; back, drops the handle and returns the host's count. It traps unless the
;; counter reads back as 7 and the count is 41, so a value that never arrived
;; cannot pass for one that did.
;;
;; The exported side is driven from Java. [static]tally.open traps when handed 0,
;; which is the value the tests never pass.
(module
  (import "example:static-functions/host-counters" "[constructor]counter" (func $new (param i32) (result i32)))
  (import "example:static-functions/host-counters" "[method]counter.get" (func $get (param i32) (result i32)))
  (import "example:static-functions/host-counters" "[static]counter.open" (func $open (param i32) (result i32)))
  (import "example:static-functions/host-counters" "[static]counter.made" (func $made (result i32)))
  (import "example:static-functions/host-counters" "[resource-drop]counter" (func $drop (param i32)))
  (import "[export]example:static-functions/guest-counters" "[resource-new]tally"
    (func $mint (param i32) (result i32)))
  (memory (export "memory") 1)
  (global $next (mut i32) (i32.const 1))
  (global $heap (mut i32) (i32.const 1024))
  (global $count (mut i32) (i32.const 0))

  (func (export "cabi_realloc") (param i32 i32 i32 i32) (result i32)
    (local $p i32)
    global.get $heap
    local.set $p
    global.get $heap
    local.get 3
    i32.add
    global.set $heap
    local.get $p)

  (func $slot (param $rep i32) (result i32)
    i32.const 300
    local.get $rep
    i32.const 4
    i32.mul
    i32.add)

  ;; Stores $value against a fresh representation and hands back a new handle.
  (func $mint-at (param $value i32) (result i32)
    (local $rep i32)
    global.get $next
    local.set $rep
    global.get $next
    i32.const 1
    i32.add
    global.set $next
    global.get $count
    i32.const 1
    i32.add
    global.set $count
    local.get $rep
    call $slot
    local.get $value
    i32.store
    local.get $rep
    call $mint)

  (func (export "run") (result i32)
    (local $h i32)
    (local $m i32)
    i32.const 7
    call $open
    local.set $h
    local.get $h
    call $get
    i32.const 7
    i32.ne
    if
      unreachable
    end
    local.get $h
    call $drop
    call $made
    local.set $m
    local.get $m
    i32.const 41
    i32.ne
    if
      unreachable
    end
    local.get $m)

  (func (export "example:static-functions/guest-counters#[constructor]tally") (param i32) (result i32)
    local.get 0
    call $mint-at)

  (func (export "example:static-functions/guest-counters#[method]tally.get") (param i32) (result i32)
    local.get 0
    call $slot
    i32.load)

  ;; Opening at 0 is what no test asks for, so it traps rather than minting.
  (func (export "example:static-functions/guest-counters#[static]tally.open") (param i32) (result i32)
    local.get 0
    i32.eqz
    if
      unreachable
    end
    local.get 0
    i32.const 100
    i32.add
    call $mint-at)

  (func (export "example:static-functions/guest-counters#[static]tally.made") (result i32)
    global.get $count)

  (func (export "example:static-functions/guest-counters#[dtor]tally") (param i32)))
