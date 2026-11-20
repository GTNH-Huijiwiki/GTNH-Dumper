package com.iouter.gtnhdumper.common.dumper;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;

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
        // 矿辞的编号和成员顺序取决于各模组的注册时机，每次启动不同；按矿辞名、成员按物品次序输出。
        Map<String, List<ItemStack>> entries = new TreeMap<>(Utils.getOreDictStacks());
        for (Map.Entry<String, List<ItemStack>> entry : entries.entrySet()) {
            String[] items = entry.getValue()
                .stream()
                .filter(java.util.Objects::nonNull)
                .sorted(StableOrder.ITEMS)
                .map(Utils::getItemKey)
                .filter(filter::matchesKey)
                .toArray(String[]::new);
            if (items.length > 0) list.add(new Object[] { entry.getKey(), items });
        }
        // 整合包一定有矿辞；空结果说明读取失败，不能当作正常数据发布。
        if (entries.isEmpty() && com.iouter.gtnhdumper.CommonProxy.isGTLoaded)
            throw new IllegalStateException("Ore dictionary is empty; refusing to export an empty oreDictionary.json");
        return list;
    }

    @Override
    public ChatComponentTranslation dumpMessage(File file) {
        return new ChatComponentTranslation("nei.options.tools.dump.gtnhdumper.oreDictionary.dumped", file.getPath());
    }
}
