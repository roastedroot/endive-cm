;; Implements example:result-values/result-values, a world handing results
;; across as parameters and fields rather than as a function's own result.
;;
;; A result flattens to its discriminant followed by the join of its payloads,
;; so result<u32, string> is three values and a bare result is one. A record of
;; a string and a result<_, level> is four values and is laid out with the name
;; at 0, the discriminant at 8 and the level at 9.
;;
;; run reports ok(7) and err("bad"), exits with ok and then err, and hands the
;; host an outcome named "flip" whose status is err(low). It traps unless the
;; host answers an outcome of the same name whose status is err(high), which
;; lands at 200.
(module
  (import "example:result-values/reporting" "report" (func $report (param i32 i32 i32)))
  (import "example:result-values/reporting" "exit" (func $exit (param i32)))
  (import "example:result-values/reporting" "reverse"
    (func $reverse (param i32 i32 i32 i32 i32)))
  (memory (export "memory") 1)
  (data (i32.const 100) "bad")
  (data (i32.const 104) "flip")
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

  (func (export "run")
    i32.const 0
    i32.const 7
    i32.const 0
    call $report
    i32.const 1
    i32.const 100
    i32.const 3
    call $report

    i32.const 0
    call $exit
    i32.const 1
    call $exit

    i32.const 104
    i32.const 4
    i32.const 1
    i32.const 0
    i32.const 200
    call $reverse

    i32.const 200
    i32.load offset=4
    i32.const 4
    i32.ne
    if
      unreachable
    end
    i32.const 200
    i32.load
    i32.load
    i32.const 0x70696c66 ;; "flip"
    i32.ne
    if
      unreachable
    end
    i32.const 200
    i32.load8_u offset=8
    i32.const 1
    i32.ne
    if
      unreachable
    end
    i32.const 200
    i32.load8_u offset=9
    i32.const 1
    i32.ne
    if
      unreachable
    end)

  ;; The error payload is a string, whose length is the third value.
  (func (export "example:result-values/checking#check")
    (param $case i32) (param $payload i32) (param $length i32) (result i32)
    local.get $case
    if (result i32)
      local.get $length
      i32.const 1000
      i32.add
    else
      local.get $payload
    end)

  (func (export "example:result-values/checking#check-bare") (param $case i32) (result i32)
    local.get $case))
