FROM docker.io/library/rust:1.99.0-slim@sha256:2752b332db73fdbb7dc576f06c82ed1f312005784ef913d7e04a28f5f55dc581

ARG WASM_TOOLS_VERSION=1.262.0
ARG WASM_TOOLS_SHA256=655b8df67d54c3ef9b52fc237c4e94bbfbb5f5f43825245cb0940a7002a9ed65
ARG WASMTIME_TAG=v50.0.1
ARG WASMTIME_COMMIT=c307ec81de02852d815e9849f7c89d955c03c30d
ARG ADAPTER_SHA256=0b4fe490390ecc9c2222f969f65b179e658a3327e005efd4ddc959766132a3c4

RUN apt-get update && \
    apt-get install -y --no-install-recommends ca-certificates curl git && \
    rm -rf /var/lib/apt/lists/*

RUN rustup target add wasm32-wasip1

WORKDIR /tmp

RUN curl -sSfL -o wasm-tools.tar.gz \
      "https://github.com/bytecodealliance/wasm-tools/releases/download/v${WASM_TOOLS_VERSION}/wasm-tools-${WASM_TOOLS_VERSION}-x86_64-linux.tar.gz" && \
    echo "${WASM_TOOLS_SHA256}  wasm-tools.tar.gz" | sha256sum -c - && \
    tar xzf wasm-tools.tar.gz --strip-components=1 -C /usr/local/bin \
      "wasm-tools-${WASM_TOOLS_VERSION}-x86_64-linux/wasm-tools" && \
    rm wasm-tools.tar.gz

RUN git clone --depth 1 --branch "${WASMTIME_TAG}" https://github.com/bytecodealliance/wasmtime.git /wasmtime && \
    test "$(git -C /wasmtime rev-parse HEAD)" = "${WASMTIME_COMMIT}"

RUN curl -sSfL -o /wasmtime/wasi_snapshot_preview1.command.wasm \
      "https://github.com/bytecodealliance/wasmtime/releases/download/${WASMTIME_TAG}/wasi_snapshot_preview1.command.wasm" && \
    echo "${ADAPTER_SHA256}  /wasmtime/wasi_snapshot_preview1.command.wasm" | sha256sum -c -

ENV CARGO_TARGET_DIR=/tmp/cargo
ENV WASMTIME_SOURCE=/wasmtime

WORKDIR /usr/code

ENTRYPOINT ["./wasmtime.sh"]
