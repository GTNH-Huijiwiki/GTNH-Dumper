package com.iouter.gtnhdumper.common.utils;

import java.lang.reflect.Field;
import java.util.List;

import net.minecraft.item.Item;

import com.iouter.gtnhdumper.GTNHDumper;

/**
 * AE2 builds its facade list once, on the first call, from the blocks registered at that moment. In some launches
 * something asks for it before the last mods have registered their blocks (block IDs above about 10900: BartWorks
 * ores, Galactic Horizons casings, NEI ore plugin dimension blocks, kitchen floors…), and the short list then stays
 * for the whole session: 6735 instead of 7014 facades, both in the NEI item list and in the facade recipes. At load
 * complete every block is registered, so the list is rebuilt there, before NEI first loads its item list.
 */
public final class LazySubtypes {

    private LazySubtypes() {}

    public static void warmUp() {
        Object facade = Item.itemRegistry.getObject("appliedenergistics2:item.ItemFacade");
        if (facade == null) return;
        try {
            Field cache = facade.getClass()
                .getDeclaredField("subTypes");
            cache.setAccessible(true);
            Object early = cache.get(facade);
            cache.set(facade, null);
            List<?> facades = (List<?>) facade.getClass()
                .getMethod("getFacades")
                .invoke(facade);
            GTNHDumper.LOG.info(
                "Rebuilt AE2 facade list at load complete: {} facades (earlier list: {})",
                facades.size(),
                early instanceof List<?>list ? list.size() : "not built");
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            GTNHDumper.LOG.warn("Could not rebuild AE2 facade list", e);
        }
    }
}
