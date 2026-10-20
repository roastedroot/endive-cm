FROM docker.io/library/rust:1.96.0-slim@sha256:c37af730be4fd8104cbf9aedbd6ab259e51ca2d5437817a0f8680edf66ac6c28

ARG WASM_TOOLS_VERSION=1.245.1
ARG WASM_TOOLS_SHA256=b171e20fd107e63e89ef6c936b5581597666a086af677d7818de92b7cdd5a86d
ARG WASMTIME_VERSION=34.0.1
ARG WASMTIME_SHA256=861714e8332d77109ece7f34afca47548408779286f32756f62ab1c77eae4117

RUN apt-get update && \
    apt-get install -y --no-install-recommends ca-certificates curl jq xz-utils && \
    rm -rf /var/lib/apt/lists/*

RUN rustup target add wasm32-wasip2 wasm32-unknown-unknown

WORKDIR /tmp

RUN curl -sSfL -o wasm-tools.tar.gz \
      "https://github.com/bytecodealliance/wasm-tools/releases/download/v${WASM_TOOLS_VERSION}/wasm-tools-${WASM_TOOLS_VERSION}-x86_64-linux.tar.gz" && \
    echo "${WASM_TOOLS_SHA256}  wasm-tools.tar.gz" | sha256sum -c - && \
    tar xzf wasm-tools.tar.gz --strip-components=1 -C /usr/local/bin \
      "wasm-tools-${WASM_TOOLS_VERSION}-x86_64-linux/wasm-tools" && \
    rm wasm-tools.tar.gz

RUN curl -sSfL -o wasmtime.tar.xz \
      "https://github.com/bytecodealliance/wasmtime/releases/download/v${WASMTIME_VERSION}/wasmtime-v${WASMTIME_VERSION}-x86_64-linux.tar.xz" && \
    echo "${WASMTIME_SHA256}  wasmtime.tar.xz" | sha256sum -c - && \
    tar xJf wasmtime.tar.xz --strip-components=1 -C /usr/local/bin \
      "wasmtime-v${WASMTIME_VERSION}-x86_64-linux/wasmtime" && \
    rm wasmtime.tar.xz

ENV CARGO_TARGET_DIR=/tmp/cargo

WORKDIR /usr/code

ENTRYPOINT ["./compile.sh"]
