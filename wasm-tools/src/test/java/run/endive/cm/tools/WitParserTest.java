package run.endive.cm.tools;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

public class WitParserTest {

    @Test
    void parseExampleWit() throws IOException {
        try (InputStream is = getClass().getResourceAsStream("/example.wit")) {
            String result = WitParser.parse(is);
            assertNotNull(result);
            assertTrue(result.contains("greet"));
            assertTrue(result.contains("add"));
            assertTrue(result.contains("host-log"));
        }
    }

    @Test
    void parseWitString() {
        String wit =
                "package test:simple;\n"
                        + "world simple {\n"
                        + "  export hello: func() -> string;\n"
                        + "}\n";
        String result = WitParser.parse(wit);
        assertNotNull(result);
        assertTrue(result.contains("hello"));
    }

    @Test
    void parseInvalidWitThrows() {
        assertThrows(WitParseException.class, () -> WitParser.parse("not valid wit {{{"));
    }
}
