package com.iouter.gtnhdumper.common.dumper;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedList;

import net.minecraft.util.ChatComponentTranslation;
import net.minecraftforge.oredict.OreDictionary;

import com.iouter.gtnhdumper.common.base.WikiDumper;
import com.iouter.gtnhdumper.common.utils.ModFilter;
import com.iouter.gtnhdumper.common.utils.StableOrder;
import com.iouter.gtnhdumper.common.utils.Utils;

public class OreDictionaryDumper extends WikiDumper {

    public OreDictionaryDumper() {
        super("tools.dump.gtnhdumper.oreDictionary");
    }

    @Override
    public boolean supportsModFilter() {
        return true;
    }

    @Override
    public int getKeyIndex() {
        return 0;
    }

    @Override
    public String getKeyStr() {
        return "oreDictionaries";
    }

    @Override
    public String[] header() {
        return new String[] { "oreDict", "items" };
    }

    @Override
    public Iterable<Object[]> dumpObject(int mode) {
        LinkedList<Object[]> list = new LinkedList<>();
        ModFilter filter = ModFilter.current();
        // 矿辞的编号和成员顺序取决于各模组的注册时机，每次启动不同；按矿辞名、成员按物品次序 输出。
        String[] names = OreDictionary.getOreNames()
            .clone();
        Arrays.sort(names);
        for (String key : names) {
            if (key == null) continue;
            String[] items = OreDictionary.getOres(key)
                .stream()
                .filter(java.util.Objects::nonNull)
                .sorted(StableOrder.ITEMS)
                .map(Utils::getItemKey)
                .filter(filter::matchesKey)
                .toArray(String[]::new);
            if (items.length > 0) list.add(new Object[] { key, items });
        }
        return list;
    }

    @Override
    public ChatComponentTranslation dumpMessage(File file) {
        return new ChatComponentTranslation("nei.options.tools.dump.gtnhdumper.oreDictionary.dumped", file.getPath());
    }
}
