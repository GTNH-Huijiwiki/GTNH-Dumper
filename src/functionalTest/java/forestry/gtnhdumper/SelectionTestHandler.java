package forestry.gtnhdumper;

import java.awt.Rectangle;
import java.util.Collections;
import java.util.List;

import net.minecraftforge.fluids.FluidTank;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.TemplateRecipeHandler;

/** Forestry namespace exercises the real fluid-tank export path without starting Minecraft. */
public class SelectionTestHandler extends TemplateRecipeHandler {

    public int tankReads;

    @Override
    public String getRecipeName() {
        return "Selection regression";
    }

    @Override
    public String getGuiTexture() {
        return "";
    }

    public class Row extends CachedRecipe {

        public final PositionedStack[] outputs;
        private final boolean selected;

        public Row(PositionedStack output, boolean selected) {
            outputs = new PositionedStack[] { output };
            this.selected = selected;
        }

        @Override
        public PositionedStack getResult() {
            return outputs[0];
        }

        @Override
        public List<PositionedStack> getIngredients() {
            throw new AssertionError("Output selection must not inspect inputs");
        }

        @Override
        public List<PositionedStack> getOtherStacks() {
            return java.util.Arrays.asList(outputs);
        }

        public List<Tank> getFluidTanks() {
            if (!selected) throw new AssertionError("Unmatched recipe's tank was inspected");
            tankReads++;
            return Collections.singletonList(new Tank());
        }
    }

    public static class Tank {

        public final Rectangle position = new Rectangle(10, 20, 16, 32);
        public final FluidTank tank = new FluidTank(1000);
    }
}
