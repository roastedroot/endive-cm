;; Implements typewire:wasi-test/filesystem, whose exports each drive one part of
;; wasi:filesystem against a single preopened directory named "data".
;;
;; A function whose flattened results exceed one value is handed a return area.
;; A result keeps its discriminant at 0 and its payload at the payload's
;; alignment, so a handle or an error-code sits at 4 and a u64 at 8. A list is a
;; pointer and a length, which the host allocates through cabi_realloc.
;;
;; Flag bits follow declaration order. Descriptor-flags read is 1 and write is
;; 2, and open-flags create is 1. Stream-error closed is case 1.
;;
;; read-file traps unless hello.txt reads back as ("hello", true).
;; write-file writes "streamed" to a new out.txt through a stream.
;; read-stream traps unless a stream from offset 1 of hello.txt, read after its
;; descriptor is dropped, gives "ello" and then closes.
;; open-error returns the error-code opening a path fails with, or 255.
;; list-directory returns how many entries the preopen holds, and traps unless
;; each is a directory or a regular file.
(module
  (import "wasi:filesystem/preopens@0.2.12" "get-directories"
    (func $get-directories (param i32)))
  (import "wasi:filesystem/types@0.2.12" "[method]descriptor.open-at"
    (func $open-at (param i32 i32 i32 i32 i32 i32 i32)))
  (import "wasi:filesystem/types@0.2.12" "[method]descriptor.read"
    (func $read (param i32 i64 i64 i32)))
  (import "wasi:filesystem/types@0.2.12" "[method]descriptor.read-via-stream"
    (func $read-via-stream (param i32 i64 i32)))
  (import "wasi:filesystem/types@0.2.12" "[method]descriptor.write-via-stream"
    (func $write-via-stream (param i32 i64 i32)))
  (import "wasi:filesystem/types@0.2.12" "[method]descriptor.read-directory"
    (func $read-directory (param i32 i32)))
  (import "wasi:filesystem/types@0.2.12" "[method]directory-entry-stream.read-directory-entry"
    (func $read-directory-entry (param i32 i32)))
  (import "wasi:filesystem/types@0.2.12" "[resource-drop]descriptor"
    (func $drop-descriptor (param i32)))
  (import "wasi:filesystem/types@0.2.12" "[resource-drop]directory-entry-stream"
    (func $drop-directory-entry-stream (param i32)))
  (import "wasi:io/streams@0.2.12" "[method]output-stream.check-write"
    (func $check-write (param i32 i32)))
  (import "wasi:io/streams@0.2.12" "[method]output-stream.write"
    (func $write (param i32 i32 i32 i32)))
  (import "wasi:io/streams@0.2.12" "[method]output-stream.blocking-flush"
    (func $blocking-flush (param i32 i32)))
  (import "wasi:io/streams@0.2.12" "[method]input-stream.blocking-read"
    (func $blocking-read (param i32 i64 i32)))
  (import "wasi:io/streams@0.2.12" "[resource-drop]output-stream"
    (func $drop-output-stream (param i32)))
  (import "wasi:io/streams@0.2.12" "[resource-drop]input-stream"
    (func $drop-input-stream (param i32)))
  (memory (export "memory") 1)
  (data (i32.const 32) "hello.txt")
  (data (i32.const 48) "out.txt")
  (data (i32.const 64) "streamed")
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

  ;; The one preopen, trapping unless there is exactly one and it is named "data".
  ;; Each element is an own<descriptor> at 0 and a name at 4.
  (func $preopen (result i32)
    (local $element i32)
    i32.const 256
    call $get-directories
    i32.const 256
    i32.load offset=4
    i32.const 1
    i32.ne
    if
      unreachable
    end
    i32.const 256
    i32.load
    local.set $element
    local.get $element
    i32.load offset=8
    i32.const 4
    i32.ne
    if
      unreachable
    end
    local.get $element
    i32.load offset=4
    i32.load
    i32.const 0x61746164 ;; "data"
    i32.ne
    if
      unreachable
    end
    local.get $element
    i32.load)

  ;; open-at on the preopen, trapping on any error.
  (func $open (param $path i32) (param $length i32) (param $open-flags i32) (param $flags i32)
    (result i32)
    call $preopen
    i32.const 0
    local.get $path
    local.get $length
    local.get $open-flags
    local.get $flags
    i32.const 288
    call $open-at
    i32.const 288
    i32.load8_u
    if
      unreachable
    end
    i32.const 288
    i32.load offset=4)

  (func (export "read-file")
    (local $file i32)
    (local $bytes i32)
    i32.const 32
    i32.const 9
    i32.const 0
    i32.const 1
    call $open
    local.set $file

    ;; read(100, 0) -> ok(("hello", true)), the tuple's list at 4 and bool at 12
    local.get $file
    i64.const 100
    i64.const 0
    i32.const 320
    call $read
    i32.const 320
    i32.load8_u
    if
      unreachable
    end
    i32.const 320
    i32.load offset=8
    i32.const 5
    i32.ne
    if
      unreachable
    end
    i32.const 320
    i32.load8_u offset=12
    i32.const 1
    i32.ne
    if
      unreachable
    end
    i32.const 320
    i32.load offset=4
    local.set $bytes
    local.get $bytes
    i32.load
    i32.const 0x6c6c6568 ;; "hell"
    i32.ne
    if
      unreachable
    end
    local.get $bytes
    i32.load8_u offset=4
    i32.const 0x6f ;; "o"
    i32.ne
    if
      unreachable
    end
    local.get $file
    call $drop-descriptor)

  (func (export "write-file")
    (local $file i32)
    (local $stream i32)
    i32.const 48
    i32.const 7
    i32.const 1
    i32.const 3
    call $open
    local.set $file

    ;; write-via-stream(0) -> ok(stream)
    local.get $file
    i64.const 0
    i32.const 352
    call $write-via-stream
    i32.const 352
    i32.load8_u
    if
      unreachable
    end
    i32.const 352
    i32.load offset=4
    local.set $stream

    ;; check-write() -> ok(n) with n of at least 8, the u64 at 8
    local.get $stream
    i32.const 368
    call $check-write
    i32.const 368
    i32.load8_u
    if
      unreachable
    end
    i32.const 368
    i64.load offset=8
    i64.const 8
    i64.lt_u
    if
      unreachable
    end

    ;; write("streamed") -> ok, then blocking-flush() -> ok
    local.get $stream
    i32.const 64
    i32.const 8
    i32.const 384
    call $write
    i32.const 384
    i32.load8_u
    if
      unreachable
    end
    local.get $stream
    i32.const 400
    call $blocking-flush
    i32.const 400
    i32.load8_u
    if
      unreachable
    end
    local.get $stream
    call $drop-output-stream
    local.get $file
    call $drop-descriptor)

  (func (export "read-stream")
    (local $file i32)
    (local $stream i32)
    i32.const 32
    i32.const 9
    i32.const 0
    i32.const 1
    call $open
    local.set $file

    ;; read-via-stream(1) -> ok(stream), whose descriptor is dropped at once
    local.get $file
    i64.const 1
    i32.const 416
    call $read-via-stream
    i32.const 416
    i32.load8_u
    if
      unreachable
    end
    i32.const 416
    i32.load offset=4
    local.set $stream
    local.get $file
    call $drop-descriptor

    ;; blocking-read(100) -> ok("ello")
    local.get $stream
    i64.const 100
    i32.const 432
    call $blocking-read
    i32.const 432
    i32.load8_u
    if
      unreachable
    end
    i32.const 432
    i32.load offset=8
    i32.const 4
    i32.ne
    if
      unreachable
    end
    i32.const 432
    i32.load offset=4
    i32.load
    i32.const 0x6f6c6c65 ;; "ello"
    i32.ne
    if
      unreachable
    end

    ;; blocking-read(100) -> err(closed)
    local.get $stream
    i64.const 100
    i32.const 432
    call $blocking-read
    i32.const 432
    i32.load8_u
    i32.const 1
    i32.ne
    if
      unreachable
    end
    i32.const 432
    i32.load8_u offset=4
    i32.const 1
    i32.ne
    if
      unreachable
    end
    local.get $stream
    call $drop-input-stream)

  (func (export "open-error") (param $path i32) (param $length i32) (result i32)
    call $preopen
    i32.const 0
    local.get $path
    local.get $length
    i32.const 0
    i32.const 1
    i32.const 448
    call $open-at
    i32.const 448
    i32.load8_u
    i32.eqz
    if
      i32.const 448
      i32.load offset=4
      call $drop-descriptor
      i32.const 255
      return
    end
    i32.const 448
    i32.load8_u offset=4)

  ;; Each entry is a result at 0, an option at 4, a type at 8 and a name at 12.
  (func (export "list-directory") (result i32)
    (local $entries i32)
    (local $count i32)
    (local $type i32)
    call $preopen
    i32.const 464
    call $read-directory
    i32.const 464
    i32.load8_u
    if
      unreachable
    end
    i32.const 464
    i32.load offset=4
    local.set $entries
    block $done
      loop $next
        local.get $entries
        i32.const 480
        call $read-directory-entry
        i32.const 480
        i32.load8_u
        if
          unreachable
        end
        i32.const 480
        i32.load8_u offset=4
        i32.eqz
        br_if $done
        i32.const 480
        i32.load8_u offset=8
        local.tee $type
        i32.const 3 ;; directory
        i32.ne
        local.get $type
        i32.const 6 ;; regular-file
        i32.ne
        i32.and
        if
          unreachable
        end
        local.get $count
        i32.const 1
        i32.add
        local.set $count
        br $next
      end
    end
    local.get $entries
    call $drop-directory-entry-stream
    local.get $count))
