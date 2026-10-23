;; Implements endive:wasi-test/cli, a command exporting wasi:cli/run.
;;
;; run writes each argument to stdout on a line of its own, then each
;; environment variable as name=value on a line of its own, then everything
;; stdin holds until it closes. It traps if stdin fails or initial-cwd names a
;; directory. Last it writes a line to stderr telling whether stdin, stdout and
;; stderr are terminals, with y or n for each.
;;
;; The first byte of the first argument decides how run ends. e returns err, s
;; exits with ok, f exits with err, and c exits with the code the rest of the
;; argument spells in decimal. Anything else returns ok.
;;
;; A function whose flattened results exceed one value is handed a return area.
;; A list is a pointer and a length, and a string in a list is another pointer
;; and length. A result or an option keeps its case at 0 and its payload at 4,
;; and a stream-error keeps its own case at 4, where closed is 1.
(module
  (import "wasi:cli/environment@0.2.12" "get-arguments" (func $get-arguments (param i32)))
  (import "wasi:cli/environment@0.2.12" "get-environment" (func $get-environment (param i32)))
  (import "wasi:cli/environment@0.2.12" "initial-cwd" (func $initial-cwd (param i32)))
  (import "wasi:cli/exit@0.2.12" "exit" (func $exit (param i32)))
  (import "wasi:cli/exit@0.2.12" "exit-with-code" (func $exit-with-code (param i32)))
  (import "wasi:cli/stdin@0.2.12" "get-stdin" (func $get-stdin (result i32)))
  (import "wasi:cli/stdout@0.2.12" "get-stdout" (func $get-stdout (result i32)))
  (import "wasi:cli/stderr@0.2.12" "get-stderr" (func $get-stderr (result i32)))
  (import "wasi:cli/terminal-input@0.2.12" "[resource-drop]terminal-input"
    (func $drop-terminal-input (param i32)))
  (import "wasi:cli/terminal-output@0.2.12" "[resource-drop]terminal-output"
    (func $drop-terminal-output (param i32)))
  (import "wasi:cli/terminal-stdin@0.2.12" "get-terminal-stdin"
    (func $get-terminal-stdin (param i32)))
  (import "wasi:cli/terminal-stdout@0.2.12" "get-terminal-stdout"
    (func $get-terminal-stdout (param i32)))
  (import "wasi:cli/terminal-stderr@0.2.12" "get-terminal-stderr"
    (func $get-terminal-stderr (param i32)))
  (import "wasi:io/streams@0.2.12" "[method]input-stream.blocking-read"
    (func $blocking-read (param i32 i64 i32)))
  (import "wasi:io/streams@0.2.12" "[method]output-stream.blocking-write-and-flush"
    (func $blocking-write-and-flush (param i32 i32 i32 i32)))
  (import "wasi:io/streams@0.2.12" "[resource-drop]input-stream"
    (func $drop-input-stream (param i32)))
  (import "wasi:io/streams@0.2.12" "[resource-drop]output-stream"
    (func $drop-output-stream (param i32)))
  (memory (export "memory") 1)
  (data (i32.const 96) "\n=")
  (data (i32.const 103) "\n")
  (global $heap (mut i32) (i32.const 1024))

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

  ;; Writes $length bytes at $pointer to $stream, trapping on any error.
  (func $write (param $stream i32) (param $pointer i32) (param $length i32)
    local.get $stream
    local.get $pointer
    local.get $length
    i32.const 64
    call $blocking-write-and-flush
    i32.const 64
    i32.load8_u
    if
      unreachable
    end)

  ;; The arguments land at 16, where run reads the first of them again.
  (func $write-arguments (param $out i32)
    (local $element i32) (local $end i32)
    i32.const 16
    call $get-arguments
    i32.const 16
    i32.load
    local.tee $element
    i32.const 16
    i32.load offset=4
    i32.const 8
    i32.mul
    i32.add
    local.set $end
    block $done
      loop $each
        local.get $element
        local.get $end
        i32.eq
        br_if $done
        local.get $out
        local.get $element
        i32.load
        local.get $element
        i32.load offset=4
        call $write
        local.get $out
        i32.const 96
        i32.const 1
        call $write
        local.get $element
        i32.const 8
        i32.add
        local.set $element
        br $each
      end
    end)

  ;; Each variable is a tuple of two strings, a name at 0 and a value at 8.
  (func $write-environment (param $out i32)
    (local $element i32) (local $end i32)
    i32.const 24
    call $get-environment
    i32.const 24
    i32.load
    local.tee $element
    i32.const 24
    i32.load offset=4
    i32.const 16
    i32.mul
    i32.add
    local.set $end
    block $done
      loop $each
        local.get $element
        local.get $end
        i32.eq
        br_if $done
        local.get $out
        local.get $element
        i32.load
        local.get $element
        i32.load offset=4
        call $write
        local.get $out
        i32.const 97
        i32.const 1
        call $write
        local.get $out
        local.get $element
        i32.load offset=8
        local.get $element
        i32.load offset=12
        call $write
        local.get $out
        i32.const 96
        i32.const 1
        call $write
        local.get $element
        i32.const 16
        i32.add
        local.set $element
        br $each
      end
    end)

  ;; Copies stdin to $out until stdin closes. The memory holding each chunk is
  ;; reused once the chunk is written, so stdin may be larger than memory.
  (func $echo-stdin (param $out i32)
    (local $in i32) (local $mark i32)
    call $get-stdin
    local.set $in
    global.get $heap
    local.set $mark
    block $closed
      loop $chunks
        local.get $in
        i64.const 4096
        i32.const 48
        call $blocking-read
        i32.const 48
        i32.load8_u
        if
          i32.const 48
          i32.load8_u offset=4
          i32.const 1
          i32.eq
          br_if $closed
          unreachable
        end
        local.get $out
        i32.const 48
        i32.load offset=4
        i32.const 48
        i32.load offset=8
        call $write
        local.get $mark
        global.set $heap
        br $chunks
      end
    end
    local.get $in
    call $drop-input-stream)

  ;; y when the option at 80 holds a terminal, and n when it is none.
  (func $answer (result i32)
    i32.const 121
    i32.const 110
    i32.const 80
    i32.load8_u
    select)

  (func $write-terminals (param $err i32)
    i32.const 80
    call $get-terminal-stdin
    i32.const 100
    call $answer
    i32.store8
    i32.const 80
    i32.load8_u
    if
      i32.const 80
      i32.load offset=4
      call $drop-terminal-input
    end
    i32.const 80
    call $get-terminal-stdout
    i32.const 101
    call $answer
    i32.store8
    i32.const 80
    i32.load8_u
    if
      i32.const 80
      i32.load offset=4
      call $drop-terminal-output
    end
    i32.const 80
    call $get-terminal-stderr
    i32.const 102
    call $answer
    i32.store8
    i32.const 80
    i32.load8_u
    if
      i32.const 80
      i32.load offset=4
      call $drop-terminal-output
    end
    local.get $err
    i32.const 100
    i32.const 4
    call $write)

  ;; The number the bytes after the first of $length bytes at $pointer spell.
  (func $parse-code (param $pointer i32) (param $length i32) (result i32)
    (local $at i32) (local $code i32)
    i32.const 1
    local.set $at
    block $done
      loop $digits
        local.get $at
        local.get $length
        i32.ge_u
        br_if $done
        local.get $code
        i32.const 10
        i32.mul
        local.get $pointer
        local.get $at
        i32.add
        i32.load8_u
        i32.const 48
        i32.sub
        i32.add
        local.set $code
        local.get $at
        i32.const 1
        i32.add
        local.set $at
        br $digits
      end
    end
    local.get $code)

  (func (export "wasi:cli/run@0.2.12#run") (result i32)
    (local $out i32) (local $err i32) (local $first i32) (local $command i32)
    i32.const 32
    call $initial-cwd
    i32.const 32
    i32.load8_u
    if
      unreachable
    end
    call $get-stdout
    local.set $out
    call $get-stderr
    local.set $err
    local.get $out
    call $write-arguments
    local.get $out
    call $write-environment
    local.get $out
    call $echo-stdin
    local.get $err
    call $write-terminals
    local.get $out
    call $drop-output-stream
    local.get $err
    call $drop-output-stream

    i32.const 16
    i32.load offset=4
    if
      i32.const 16
      i32.load
      local.tee $first
      i32.load offset=4
      if
        local.get $first
        i32.load
        i32.load8_u
        local.set $command
      end
    end
    local.get $command
    i32.const 0x65 ;; e
    i32.eq
    if
      i32.const 1
      return
    end
    local.get $command
    i32.const 0x73 ;; s
    i32.eq
    if
      i32.const 0
      call $exit
      unreachable
    end
    local.get $command
    i32.const 0x66 ;; f
    i32.eq
    if
      i32.const 1
      call $exit
      unreachable
    end
    local.get $command
    i32.const 0x63 ;; c
    i32.eq
    if
      local.get $first
      i32.load
      local.get $first
      i32.load offset=4
      call $parse-code
      call $exit-with-code
      unreachable
    end
    i32.const 0))
