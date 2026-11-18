;; Implements typewire:wasi-test/ip-name-lookup, whose exports each resolve a name
;; through wasi:sockets/ip-name-lookup on the instance network.
;;
;; resolve-addresses hands back a result whose own<resolve-address-stream> or
;; error-code sits at 4. resolve-next-address hands back a
;; result<option<ip-address>, error-code>, whose payload is aligned to 2. So the
;; error-code or the option discriminant sits at 2, the ip-address discriminant
;; at 4, and its octets from 6.
;;
;; Each export waits on the stream's pollable before reading from it.
;; count returns how many addresses the name has, and traps on any error.
;; first-ipv4 returns the first address packed big-endian into a u32, and traps
;; unless there is one and it is IPv4.
;; lookup-error returns the error-code resolving the name fails with, or 255.
(module
  (import "wasi:sockets/instance-network@0.2.12" "instance-network"
    (func $instance-network (result i32)))
  (import "wasi:sockets/network@0.2.12" "[resource-drop]network"
    (func $drop-network (param i32)))
  (import "wasi:sockets/ip-name-lookup@0.2.12" "resolve-addresses"
    (func $resolve-addresses (param i32 i32 i32 i32)))
  (import "wasi:sockets/ip-name-lookup@0.2.12" "[method]resolve-address-stream.resolve-next-address"
    (func $resolve-next-address (param i32 i32)))
  (import "wasi:sockets/ip-name-lookup@0.2.12" "[method]resolve-address-stream.subscribe"
    (func $subscribe (param i32) (result i32)))
  (import "wasi:sockets/ip-name-lookup@0.2.12" "[resource-drop]resolve-address-stream"
    (func $drop-stream (param i32)))
  (import "wasi:io/poll@0.2.12" "[method]pollable.block"
    (func $block (param i32)))
  (import "wasi:io/poll@0.2.12" "[resource-drop]pollable"
    (func $drop-pollable (param i32)))
  (memory (export "memory") 1)
  (global $heap (mut i32) (i32.const 4096))

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

  ;; resolve-addresses on the instance network, leaving its result at 256.
  (func $start (param $name i32) (param $length i32)
    (local $network i32)
    call $instance-network
    local.set $network
    local.get $network
    local.get $name
    local.get $length
    i32.const 256
    call $resolve-addresses
    local.get $network
    call $drop-network)

  ;; A stream resolving the name, waited on until it is ready, trapping on any error.
  (func $ready-stream (param $name i32) (param $length i32) (result i32)
    (local $stream i32)
    local.get $name
    local.get $length
    call $start
    i32.const 256
    i32.load8_u
    if
      unreachable
    end
    i32.const 256
    i32.load offset=4
    local.set $stream
    local.get $stream
    call $wait
    local.get $stream)

  (func $wait (param $stream i32)
    (local $pollable i32)
    local.get $stream
    call $subscribe
    local.set $pollable
    local.get $pollable
    call $block
    local.get $pollable
    call $drop-pollable)

  ;; resolve-next-address, leaving its result at 288.
  (func $next (param $stream i32)
    local.get $stream
    i32.const 288
    call $resolve-next-address)

  (func (export "count") (param $name i32) (param $length i32) (result i32)
    (local $stream i32)
    (local $count i32)
    local.get $name
    local.get $length
    call $ready-stream
    local.set $stream
    block $done
      loop $more
        local.get $stream
        call $next
        i32.const 288
        i32.load8_u
        if
          unreachable
        end
        i32.const 288
        i32.load8_u offset=2
        i32.eqz
        br_if $done
        local.get $count
        i32.const 1
        i32.add
        local.set $count
        br $more
      end
    end
    local.get $stream
    call $drop-stream
    local.get $count)

  (func (export "first-ipv4") (param $name i32) (param $length i32) (result i32)
    (local $stream i32)
    local.get $name
    local.get $length
    call $ready-stream
    local.set $stream
    local.get $stream
    call $next
    local.get $stream
    call $drop-stream
    ;; ok, then some, then ipv4
    i32.const 288
    i32.load8_u
    if
      unreachable
    end
    i32.const 288
    i32.load8_u offset=2
    i32.eqz
    if
      unreachable
    end
    i32.const 288
    i32.load8_u offset=4
    if
      unreachable
    end
    i32.const 288
    i32.load8_u offset=6
    i32.const 24
    i32.shl
    i32.const 288
    i32.load8_u offset=7
    i32.const 16
    i32.shl
    i32.or
    i32.const 288
    i32.load8_u offset=8
    i32.const 8
    i32.shl
    i32.or
    i32.const 288
    i32.load8_u offset=9
    i32.or)

  (func (export "lookup-error") (param $name i32) (param $length i32) (result i32)
    (local $stream i32)
    local.get $name
    local.get $length
    call $start
    i32.const 256
    i32.load8_u
    if
      i32.const 256
      i32.load8_u offset=4
      return
    end
    i32.const 256
    i32.load offset=4
    local.set $stream
    local.get $stream
    call $wait
    local.get $stream
    call $next
    local.get $stream
    call $drop-stream
    i32.const 288
    i32.load8_u
    if
      i32.const 288
      i32.load8_u offset=2
      return
    end
    i32.const 255)
)
