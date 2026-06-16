package run.endive.cm.parser;

import run.endive.cm.types.Section;

@FunctionalInterface
public interface ComponentParserListener {

    void onSection(Section section);
}
