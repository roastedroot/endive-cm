;; Implements endive:wasi-test/udp, whose exports use wasi:sockets/udp on the
;; instance network, always over IPv4 loopback.
;;
;; bind binds a socket to an ephemeral port, opens streams to any address on it,
;; and returns the port.
;; echo-one receives one datagram on those streams and sends it back to its sender.
;; request binds a new socket, limits its streams to a port, sends the message,
;; and returns the first datagram it receives.
;; request-error does the same with a one-byte message, and returns the
;; error-code it fails with, or 255.
;; Every other export traps on any error.
;;
;; An ip-socket-address flattens to its discriminant and 11 payload slots. An
;; IPv4 address fills the first 5 with its port and octets. An option of one adds
;; its own discriminant before them.
;;
;; Results land at 256. A result<_, error-code> keeps its error-code at 1, a
;; result<u64, error-code> keeps its payload at 8, and every other result keeps
;; its payload at 4. A local-address result keeps its port at 8.
;;
;; An incoming-datagram keeps its data at 0 and its remote-address at 8. The
;; outgoing-datagram sent is built at 512, with its data at 0, the option's
;; discriminant at 8, and the remote-address at 12. Each ip-socket-address takes
;; 32 bytes.
(module
  (import "wasi:sockets/instance-network@0.2.12" "instance-network"
    (func $instance-network (result i32)))
  (import "wasi:sockets/network@0.2.12" "[resource-drop]network"
    (func $drop-network (param i32)))
  (import "wasi:sockets/udp-create-socket@0.2.12" "create-udp-socket"
    (func $create-udp-socket (param i32 i32)))
  (import "wasi:sockets/udp@0.2.12" "[method]udp-socket.start-bind"
    (func $start-bind (param i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32)))
  (import "wasi:sockets/udp@0.2.12" "[method]udp-socket.finish-bind"
    (func $finish-bind (param i32 i32)))
  (import "wasi:sockets/udp@0.2.12" "[method]udp-socket.stream"
    (func $stream (param i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32)))
  (import "wasi:sockets/udp@0.2.12" "[method]udp-socket.local-address"
    (func $local-address (param i32 i32)))
  (import "wasi:sockets/udp@0.2.12" "[method]udp-socket.subscribe"
    (func $subscribe-socket (param i32) (result i32)))
  (import "wasi:sockets/udp@0.2.12" "[resource-drop]udp-socket"
    (func $drop-socket (param i32)))
  (import "wasi:sockets/udp@0.2.12" "[method]incoming-datagram-stream.receive"
    (func $receive (param i32 i64 i32)))
  (import "wasi:sockets/udp@0.2.12" "[method]incoming-datagram-stream.subscribe"
    (func $subscribe-incoming (param i32) (result i32)))
  (import "wasi:sockets/udp@0.2.12" "[resource-drop]incoming-datagram-stream"
    (func $drop-incoming (param i32)))
  (import "wasi:sockets/udp@0.2.12" "[method]outgoing-datagram-stream.check-send"
    (func $check-send (param i32 i32)))
  (import "wasi:sockets/udp@0.2.12" "[method]outgoing-datagram-stream.send"
    (func $send (param i32 i32 i32 i32)))
  (import "wasi:sockets/udp@0.2.12" "[method]outgoing-datagram-stream.subscribe"
    (func $subscribe-outgoing (param i32) (result i32)))
  (import "wasi:sockets/udp@0.2.12" "[resource-drop]outgoing-datagram-stream"
    (func $drop-outgoing (param i32)))
  (import "wasi:io/poll@0.2.12" "[method]pollable.block"
    (func $block (param i32)))
  (import "wasi:io/poll@0.2.12" "[resource-drop]pollable"
    (func $drop-pollable (param i32)))
  (memory (export "memory") 2)
  (data (i32.const 700) "?")
  (global $heap (mut i32) (i32.const 65536))
  (global $incoming (mut i32) (i32.const 0))
  (global $outgoing (mut i32) (i32.const 0))
  (global $received (mut i32) (i32.const 0))

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

  (func $wait (param $pollable i32)
    local.get $pollable
    call $block
    local.get $pollable
    call $drop-pollable)

  ;; Traps unless the code is 255, which stands for success.
  (func $ok (param $code i32)
    local.get $code
    i32.const 255
    i32.ne
    if
      unreachable
    end)

  ;; Drops whichever of the streams and the socket is not 0.
  (func $close (param $in i32) (param $out i32) (param $socket i32)
    local.get $out
    if
      local.get $out
      call $drop-outgoing
    end
    local.get $in
    if
      local.get $in
      call $drop-incoming
    end
    local.get $socket
    if
      local.get $socket
      call $drop-socket
    end)

  ;; Creates an IPv4 socket and binds it to 127.0.0.1 on an ephemeral port,
  ;; leaving the socket at 260. The error-code, or 255.
  (func $bound-socket (result i32)
    (local $socket i32)
    (local $network i32)
    i32.const 0
    i32.const 256
    call $create-udp-socket
    i32.const 256
    i32.load8_u
    if
      i32.const 256
      i32.load8_u offset=4
      return
    end
    i32.const 256
    i32.load offset=4
    local.set $socket
    call $instance-network
    local.set $network
    local.get $socket
    local.get $network
    i32.const 0
    i32.const 0
    i32.const 127
    i32.const 0
    i32.const 0
    i32.const 1
    i32.const 0
    i32.const 0
    i32.const 0
    i32.const 0
    i32.const 0
    i32.const 0
    i32.const 256
    call $start-bind
    local.get $network
    call $drop-network
    i32.const 256
    i32.load8_u
    if
      local.get $socket
      call $drop-socket
      i32.const 256
      i32.load8_u offset=1
      return
    end
    block $bound
      loop $retry
        local.get $socket
        i32.const 256
        call $finish-bind
        i32.const 256
        i32.load8_u
        i32.eqz
        br_if $bound
        i32.const 256
        i32.load8_u offset=1
        i32.const 8
        i32.ne
        if
          local.get $socket
          call $drop-socket
          i32.const 256
          i32.load8_u offset=1
          return
        end
        local.get $socket
        call $subscribe-socket
        call $wait
        br $retry
      end
    end
    i32.const 260
    local.get $socket
    i32.store
    i32.const 255)

  ;; Opens streams on the socket, limited to 127.0.0.1 on the port unless it is 0,
  ;; leaving them in $incoming and $outgoing. The error-code, or 255.
  (func $open-streams (param $socket i32) (param $port i32) (result i32)
    local.get $port
    if
      local.get $socket
      i32.const 1
      i32.const 0
      local.get $port
      i32.const 127
      i32.const 0
      i32.const 0
      i32.const 1
      i32.const 0
      i32.const 0
      i32.const 0
      i32.const 0
      i32.const 0
      i32.const 0
      i32.const 256
      call $stream
    else
      local.get $socket
      i32.const 0
      i32.const 0
      i32.const 0
      i32.const 0
      i32.const 0
      i32.const 0
      i32.const 0
      i32.const 0
      i32.const 0
      i32.const 0
      i32.const 0
      i32.const 0
      i32.const 0
      i32.const 256
      call $stream
    end
    i32.const 256
    i32.load8_u
    if
      i32.const 256
      i32.load8_u offset=4
      return
    end
    i32.const 256
    i32.load offset=4
    global.set $incoming
    i32.const 256
    i32.load offset=8
    global.set $outgoing
    i32.const 255)

  ;; Waits for one datagram on $incoming, leaving it in $received. The
  ;; error-code, or 255.
  (func $receive-one (result i32)
    block $received
      loop $retry
        global.get $incoming
        i64.const 1
        i32.const 256
        call $receive
        i32.const 256
        i32.load8_u
        if
          i32.const 256
          i32.load8_u offset=4
          return
        end
        i32.const 256
        i32.load offset=8
        br_if $received
        global.get $incoming
        call $subscribe-incoming
        call $wait
        br $retry
      end
    end
    i32.const 256
    i32.load offset=4
    global.set $received
    i32.const 255)

  ;; Waits until $outgoing permits a datagram, then sends the one built at 512.
  ;; The error-code, or 255.
  (func $send-one (result i32)
    block $permitted
      loop $retry
        global.get $outgoing
        i32.const 256
        call $check-send
        i32.const 256
        i32.load8_u
        if
          i32.const 256
          i32.load8_u offset=8
          return
        end
        i32.const 256
        i64.load offset=8
        i64.const 0
        i64.ne
        br_if $permitted
        global.get $outgoing
        call $subscribe-outgoing
        call $wait
        br $retry
      end
    end
    global.get $outgoing
    i32.const 512
    i32.const 1
    i32.const 256
    call $send
    i32.const 256
    i32.load8_u
    if
      i32.const 256
      i32.load8_u offset=8
      return
    end
    i32.const 256
    i64.load offset=8
    i64.const 1
    i64.ne
    if
      unreachable
    end
    i32.const 255)

  ;; Binds a new socket, limits its streams to the port, sends the message, and
  ;; leaves the first datagram received in $received. The error-code, or 255.
  (func $exchange (param $port i32) (param $message i32) (param $length i32) (result i32)
    (local $socket i32)
    (local $code i32)
    call $bound-socket
    local.tee $code
    i32.const 255
    i32.ne
    if
      local.get $code
      return
    end
    i32.const 260
    i32.load
    local.set $socket
    local.get $socket
    local.get $port
    call $open-streams
    local.tee $code
    i32.const 255
    i32.ne
    if
      i32.const 0
      i32.const 0
      local.get $socket
      call $close
      local.get $code
      return
    end
    i32.const 512
    local.get $message
    i32.store
    i32.const 512
    local.get $length
    i32.store offset=4
    i32.const 512
    i32.const 0
    i32.store8 offset=8
    block $done
      call $send-one
      local.tee $code
      i32.const 255
      i32.ne
      br_if $done
      call $receive-one
      local.set $code
    end
    global.get $incoming
    global.get $outgoing
    local.get $socket
    call $close
    local.get $code)

  (func (export "bind") (result i32)
    (local $socket i32)
    call $bound-socket
    call $ok
    i32.const 260
    i32.load
    local.set $socket
    local.get $socket
    i32.const 0
    call $open-streams
    call $ok
    local.get $socket
    i32.const 256
    call $local-address
    i32.const 256
    i32.load8_u
    if
      unreachable
    end
    i32.const 256
    i32.load16_u offset=8)

  (func (export "echo-one")
    call $receive-one
    call $ok
    i32.const 512
    global.get $received
    i32.load
    i32.store
    i32.const 512
    global.get $received
    i32.load offset=4
    i32.store offset=4
    i32.const 512
    i32.const 1
    i32.store8 offset=8
    i32.const 524
    global.get $received
    i32.const 8
    i32.add
    i32.const 32
    memory.copy
    call $send-one
    call $ok)

  ;; Returns the received datagram's data, which lies in the guest's memory.
  (func (export "request") (param $port i32) (param $message i32) (param $length i32) (result i32)
    local.get $port
    local.get $message
    local.get $length
    call $exchange
    call $ok
    i32.const 600
    global.get $received
    i32.load
    i32.store
    i32.const 600
    global.get $received
    i32.load offset=4
    i32.store offset=4
    i32.const 600)

  (func (export "request-error") (param $port i32) (result i32)
    local.get $port
    i32.const 700
    i32.const 1
    call $exchange)
)
