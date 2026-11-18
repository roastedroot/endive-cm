package typewire.testing;

import io.roastedroot.typewire.runtime.Bindgen;

@Bindgen(world = "hello-world", path = "wit/world-exports.wit")
public class WorldExportsHost {}
