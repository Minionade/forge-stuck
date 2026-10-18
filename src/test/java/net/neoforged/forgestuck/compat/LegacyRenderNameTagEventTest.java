package net.neoforged.forgestuck.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.common.util.TriState;
import org.junit.jupiter.api.Test;

class LegacyRenderNameTagEventTest {
    @Test
    void translatesTriStateToForgeResult() {
        RenderNameTagEvent event = new RenderNameTagEvent(null, null, null, null, null, 0, 0);

        assertEquals(LegacyEventResult.DEFAULT, LegacyRenderNameTagEvent.getResult(event));
        event.setCanRender(TriState.TRUE);
        assertEquals(LegacyEventResult.ALLOW, LegacyRenderNameTagEvent.getResult(event));
        event.setCanRender(TriState.FALSE);
        assertEquals(LegacyEventResult.DENY, LegacyRenderNameTagEvent.getResult(event));
    }
}
