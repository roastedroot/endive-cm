package io.roastedroot.typewire.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.roastedroot.typewire.abi.ResourceValue;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The table mapping a resource representation to the Java object it stands for, which generated
 * bindings convert every host resource handle through.
 */
public class HostResourceTableTests {

    /** Taking ownership forgets the value without dropping it, since it now belongs to the taker. */
    @Test
    public void takingAValueForgetsItWithoutDroppingIt() {
        var table = new HostResourceTable<String>();
        var type = HostInstance.builder(new ComponentStore()).declareResource(null).type();
        int rep = table.add("conn");
        List<String> dropped = new ArrayList<>();

        String taken = table.take(ResourceValue.owned(type, rep));
        table.drop(rep, dropped::add);

        assertSame("conn", taken);
        assertEquals(0, table.size());
        assertEquals(List.of(), dropped);
    }

    /** A handle whose value was taken names nothing, so taking it again is refused. */
    @Test
    public void aTakenValueCannotBeTakenAgain() {
        var table = new HostResourceTable<String>();
        var type = HostInstance.builder(new ComponentStore()).declareResource(null).type();
        ResourceValue handle = ResourceValue.owned(type, table.add("conn"));

        table.take(handle);

        assertThrows(LinkageException.class, () -> table.take(handle));
    }
}
