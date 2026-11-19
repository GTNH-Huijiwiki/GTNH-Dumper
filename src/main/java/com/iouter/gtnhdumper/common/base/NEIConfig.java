package com.iouter.gtnhdumper.common.base;

import com.iouter.gtnhdumper.CommonProxy;
import com.iouter.gtnhdumper.GTNHDumper;
import com.iouter.gtnhdumper.common.dumper.AdvItemPanelDumper;
import com.iouter.gtnhdumper.common.dumper.FluidsDumper;
import com.iouter.gtnhdumper.common.dumper.GTMaterialDumper;
import com.iouter.gtnhdumper.common.dumper.GTNHDimensionDumper;
import com.iouter.gtnhdumper.common.dumper.ItemIconDumper;
import com.iouter.gtnhdumper.common.dumper.OreDictionaryDumper;
import com.iouter.gtnhdumper.common.dumper.RecipesDumper;
import com.iouter.gtnhdumper.common.dumper.TC4ThaumonomiconDumper;
import com.iouter.gtnhdumper.common.dumper.TICMaterialDumper;

import codechicken.nei.api.API;
import codechicken.nei.api.IConfigureNEI;

public class NEIConfig implements IConfigureNEI {

    @Override
    public void loadConfig() {
        API.addOption(ModFilterOption.INSTANCE);
        API.addOption(new AdvItemPanelDumper());
        API.addOption(new ItemIconDumper());
        API.addOption(new RecipesDumper());
        API.addOption(new OreDictionaryDumper());
        API.addOption(new FluidsDumper());
        if (CommonProxy.isGTLoaded) {
            API.addOption(new GTMaterialDumper());
            API.addOption(new GTNHDimensionDumper());
        }
        if (CommonProxy.isTiCLoaded) API.addOption(new TICMaterialDumper());
        if (CommonProxy.isTCLoaded) API.addOption(new TC4ThaumonomiconDumper());
    }

    @Override
    public String getName() {
        return GTNHDumper.MOD_ID;
    }

    @Override
    public String getVersion() {
        return GTNHDumper.VERSION;
    }
}
