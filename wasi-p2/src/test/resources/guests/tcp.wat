;; Implements endive:wasi-test/tcp, whose exports use wasi:sockets/tcp on the
;; instance network, always over IPv4 loopback.
;;
;; listen binds a socket to an ephemeral port, listens on it, and returns the port.
;; echo-one accepts one connection on that socket and writes back everything it
;; reads until the peer stops sending.
;; request connects to a port, sends the message, stops sending, and returns
;; everything it reads until the peer closes the connection.
;; connect-error returns the error-code connecting to a port fails with, or 255.
;; Every other export traps on any error.
;;
;; An ip-socket-address flattens to its discriminant and 11 payload slots. An
;; IPv4 address fills the first 5 with its port and octets.
;;
;; Socket results land at 256. A result<_, error-code> keeps its error-code at 1,
;; and every other result keeps its payload at 4. A local-address result keeps
;; its port at 8. Stream read results land at 288 and write results at 320, each
;; with its payload at 4. A stream-error of 1 is closed.
(module
  (import "wasi:sockets/instance-network@0.2.12" "instance-network"
    (func $instance-network (result i32)))
  (import "wasi:sockets/network@0.2.12" "[resource-drop]network"
    (func $drop-network (param i32)))
  (import "wasi:sockets/tcp-create-socket@0.2.12" "create-tcp-socket"
    (func $create-tcp-socket (param i32 i32)))
  (import "wasi:sockets/tcp@0.2.12" "[method]tcp-socket.start-bind"
    (func $start-bind (param i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32)))
  (import "wasi:sockets/tcp@0.2.12" "[method]tcp-socket.finish-bind"
    (func $finish-bind (param i32 i32)))
  (import "wasi:sockets/tcp@0.2.12" "[method]tcp-socket.start-connect"
    (func $start-connect (param i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32)))
  (import "wasi:sockets/tcp@0.2.12" "[method]tcp-socket.finish-connect"
    (func $finish-connect (param i32 i32)))
  (import "wasi:sockets/tcp@0.2.12" "[method]tcp-socket.start-listen"
    (func $start-listen (param i32 i32)))
  (import "wasi:sockets/tcp@0.2.12" "[method]tcp-socket.finish-listen"
    (func $finish-listen (param i32 i32)))
  (import "wasi:sockets/tcp@0.2.12" "[method]tcp-socket.accept"
    (func $accept (param i32 i32)))
  (import "wasi:sockets/tcp@0.2.12" "[method]tcp-socket.local-address"
    (func $local-address (param i32 i32)))
  (import "wasi:sockets/tcp@0.2.12" "[method]tcp-socket.shutdown"
    (func $shutdown (param i32 i32 i32)))
  (import "wasi:sockets/tcp@0.2.12" "[method]tcp-socket.subscribe"
    (func $subscribe (param i32) (result i32)))
  (import "wasi:sockets/tcp@0.2.12" "[resource-drop]tcp-socket"
    (func $drop-socket (param i32)))
  (import "wasi:io/poll@0.2.12" "[method]pollable.block"
    (func $block (param i32)))
  (import "wasi:io/poll@0.2.12" "[resource-drop]pollable"
    (func $drop-pollable (param i32)))
  (import "wasi:io/streams@0.2.12" "[method]input-stream.blocking-read"
    (func $blocking-read (param i32 i64 i32)))
  (import "wasi:io/streams@0.2.12" "[method]output-stream.blocking-write-and-flush"
    (func $blocking-write-and-flush (param i32 i32 i32 i32)))
  (import "wasi:io/streams@0.2.12" "[resource-drop]input-stream"
    (func $drop-input (param i32)))
  (import "wasi:io/streams@0.2.12" "[resource-drop]output-stream"
    (func $drop-output (param i32)))
  (memory (export "memory") 2)
  (global $heap (mut i32) (i32.const 65536))
  (global $listener (mut i32) (i32.const 0))

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

  ;; create-tcp-socket for IPv4, leaving its result at 256.
  (func $create
    i32.const 0
    i32.const 256
    call $create-tcp-socket)

  ;; A new IPv4 socket.
  (func $new-socket (result i32)
    call $create
    call $ok
    i32.const 256
    i32.load offset=4)

  ;; start-bind to 127.0.0.1 on an ephemeral port, leaving its result at 256.
  (func $bind (param $socket i32)
    (local $network i32)
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
    call $drop-network)

  ;; start-connect to 127.0.0.1 on the port, leaving its result at 256.
  (func $connect (param $socket i32) (param $port i32)
    (local $network i32)
    call $instance-network
    local.set $network
    local.get $socket
    local.get $network
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
    call $start-connect
    local.get $network
    call $drop-network)

  (func $wait (param $socket i32)
    (local $pollable i32)
    local.get $socket
    call $subscribe
    local.set $pollable
    local.get $pollable
    call $block
    local.get $pollable
    call $drop-pollable)

  ;; Traps unless the result at 256 is ok.
  (func $ok
    i32.const 256
    i32.load8_u
    if
      unreachable
    end)

  ;; Whether the result at 256 is would-block, trapping on any other error.
  (func $pending (param $offset i32) (result i32)
    i32.const 256
    i32.load8_u
    i32.eqz
    if
      i32.const 0
      return
    end
    i32.const 256
    local.get $offset
    i32.add
    i32.load8_u
    i32.const 8
    i32.ne
    if
      unreachable
    end
    i32.const 1)

  ;; blocking-read up to 4096 bytes, leaving its result at 288. Whether the
  ;; stream is closed, trapping on any other error.
  (func $read (param $in i32) (result i32)
    local.get $in
    i64.const 4096
    i32.const 288
    call $blocking-read
    i32.const 288
    i32.load8_u
    i32.eqz
    if
      i32.const 0
      return
    end
    i32.const 288
    i32.load8_u offset=4
    i32.const 1
    i32.ne
    if
      unreachable
    end
    i32.const 1)

  ;; blocking-write-and-flush in chunks of at most 4096 bytes, the most each call takes.
  (func $write (param $out i32) (param $bytes i32) (param $length i32)
    (local $chunk i32)
    block $written
      loop $more
        local.get $length
        i32.eqz
        br_if $written
        i32.const 4096
        local.get $length
        local.get $length
        i32.const 4096
        i32.gt_u
        select
        local.set $chunk
        local.get $out
        local.get $bytes
        local.get $chunk
        i32.const 320
        call $blocking-write-and-flush
        i32.const 320
        i32.load8_u
        if
          unreachable
        end
        local.get $bytes
        local.get $chunk
        i32.add
        local.set $bytes
        local.get $length
        local.get $chunk
        i32.sub
        local.set $length
        br $more
      end
    end)

  (func (export "listen") (result i32)
    (local $socket i32)
    call $new-socket
    local.set $socket
    local.get $socket
    call $bind
    call $ok
    block $bound
      loop $retry
        local.get $socket
        i32.const 256
        call $finish-bind
        i32.const 1
        call $pending
        i32.eqz
        br_if $bound
        local.get $socket
        call $wait
        br $retry
      end
    end
    local.get $socket
    i32.const 256
    call $start-listen
    call $ok
    block $listening
      loop $retry
        local.get $socket
        i32.const 256
        call $finish-listen
        i32.const 1
        call $pending
        i32.eqz
        br_if $listening
        local.get $socket
        call $wait
        br $retry
      end
    end
    local.get $socket
    global.set $listener
    local.get $socket
    i32.const 256
    call $local-address
    call $ok
    i32.const 256
    i32.load16_u offset=8)

  (func (export "echo-one")
    (local $socket i32)
    (local $in i32)
    (local $out i32)
    (local $mark i32)
    block $accepted
      loop $retry
        global.get $listener
        i32.const 256
        call $accept
        i32.const 4
        call $pending
        i32.eqz
        br_if $accepted
        global.get $listener
        call $wait
        br $retry
      end
    end
    i32.const 256
    i32.load offset=4
    local.set $socket
    i32.const 256
    i32.load offset=8
    local.set $in
    i32.const 256
    i32.load offset=12
    local.set $out
    global.get $heap
    local.set $mark
    block $closed
      loop $more
        local.get $in
        call $read
        br_if $closed
        local.get $out
        i32.const 288
        i32.load offset=4
        i32.const 288
        i32.load offset=8
        call $write
        local.get $mark
        global.set $heap
        br $more
      end
    end
    local.get $out
    call $drop-output
    local.get $in
    call $drop-input
    local.get $socket
    call $drop-socket)

  ;; Collects the response from 4096, below the heap.
  (func (export "request") (param $port i32) (param $message i32) (param $length i32) (result i32)
    (local $socket i32)
    (local $in i32)
    (local $out i32)
    (local $mark i32)
    (local $received i32)
    (local $chunk i32)
    call $new-socket
    local.set $socket
    local.get $socket
    local.get $port
    call $connect
    call $ok
    block $connected
      loop $retry
        local.get $socket
        i32.const 256
        call $finish-connect
        i32.const 4
        call $pending
        i32.eqz
        br_if $connected
        local.get $socket
        call $wait
        br $retry
      end
    end
    i32.const 256
    i32.load offset=4
    local.set $in
    i32.const 256
    i32.load offset=8
    local.set $out
    local.get $out
    local.get $message
    local.get $length
    call $write
    local.get $socket
    i32.const 1
    i32.const 256
    call $shutdown
    call $ok
    global.get $heap
    local.set $mark
    block $closed
      loop $more
        local.get $in
        call $read
        br_if $closed
        i32.const 288
        i32.load offset=8
        local.set $chunk
        local.get $received
        local.get $chunk
        i32.add
        i32.const 61440
        i32.gt_u
        if
          unreachable
        end
        i32.const 4096
        local.get $received
        i32.add
        i32.const 288
        i32.load offset=4
        local.get $chunk
        memory.copy
        local.get $received
        local.get $chunk
        i32.add
        local.set $received
        local.get $mark
        global.set $heap
        br $more
      end
    end
    local.get $out
    call $drop-output
    local.get $in
    call $drop-input
    local.get $socket
    call $drop-socket
    i32.const 512
    i32.const 4096
    i32.store
    i32.const 512
    local.get $received
    i32.store offset=4
    i32.const 512)

  (func (export "connect-error") (param $port i32) (result i32)
    (local $socket i32)
    (local $code i32)
    call $create
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
    local.get $socket
    local.get $port
    call $connect
    i32.const 256
    i32.load8_u
    if
      i32.const 256
      i32.load8_u offset=1
      local.set $code
      local.get $socket
      call $drop-socket
      local.get $code
      return
    end
    block $connected
      loop $retry
        local.get $socket
        i32.const 256
        call $finish-connect
        i32.const 256
        i32.load8_u
        i32.eqz
        br_if $connected
        i32.const 256
        i32.load8_u offset=4
        local.set $code
        local.get $code
        i32.const 8
        i32.ne
        if
          local.get $socket
          call $drop-socket
          local.get $code
          return
        end
        local.get $socket
        call $wait
        br $retry
      end
    end
    i32.const 256
    i32.load offset=8
    call $drop-output
    i32.const 256
    i32.load offset=4
    call $drop-input
    local.get $socket
    call $drop-socket
    i32.const 255)
)
