;; Implements example:flag-types/flag-types, using the same WIT the golden
;; fixture is generated from.
;;
;; Flags of three labels flatten to one i32, so read is bit 0, write bit 1 and
;; exec bit 2. run asks the host to grant what it was handed and traps unless
;; the host grants exactly the complement, which is what shows the bits
;; crossing in both directions rather than arriving empty.
(module
  (import "example:flag-types/permissions" "grant" (func $grant (param i32) (result i32)))

  (func (export "example:flag-types/runner#run") (param $requested i32) (result i32)
    (local $granted i32)
    local.get $requested
    call $grant
    local.set $granted
    local.get $granted
    local.get $requested
    i32.const 7
    i32.xor
    i32.ne
    if
      unreachable
    end
    local.get $granted))
