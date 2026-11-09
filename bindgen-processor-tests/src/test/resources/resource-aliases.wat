;; Implements example:resource-aliases/resource-aliases, a world built for this
;; test because none of the bindgen! examples aliases a resource the way
;; wasi:http aliases fields as headers.
;;
;; A handle is an i32 index into the guest's table. The guest makes a fields,
;; hands it to the request constructor as headers, gets it back as headers and
;; reads it through a fields method and through count. Each step only links and
;; runs if both names are one resource type.
;;
;; run traps unless the fields method and count agree, and returns the total.
(module
  (import "example:resource-aliases/types" "[constructor]fields" (func $fields (result i32)))
  (import "example:resource-aliases/types" "[method]fields.add" (func $add (param i32 i32)))
  (import "example:resource-aliases/types" "[method]fields.total"
    (func $total (param i32) (result i32)))
  (import "example:resource-aliases/types" "[constructor]request"
    (func $request (param i32) (result i32)))
  (import "example:resource-aliases/types" "[method]request.headers"
    (func $headers (param i32) (result i32)))
  (import "example:resource-aliases/types" "count" (func $count (param i32) (result i32)))

  (func (export "run") (result i32)
    (local $fields i32)
    (local $headers i32)
    (local $total i32)

    call $fields
    local.set $fields
    local.get $fields
    i32.const 3
    call $add
    local.get $fields
    i32.const 4
    call $add

    ;; request(fields).headers()
    local.get $fields
    call $request
    call $headers
    local.set $headers

    local.get $headers
    call $total
    local.set $total
    local.get $headers
    call $count
    local.get $total
    i32.ne
    if
      unreachable
    end
    local.get $total))
