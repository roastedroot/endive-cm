package io.roastedroot.typewire.parser;

import io.roastedroot.typewire.types.Section;

@FunctionalInterface
public interface ComponentParserListener {

    void onSection(Section section);
}
