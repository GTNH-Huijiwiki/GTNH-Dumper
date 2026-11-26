package com.iouter.gtnhdumper.common.utils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

import com.iouter.gtnhdumper.GTNHDumper;

import cpw.mods.fml.common.registry.GameData;
import forestry.api.apiculture.BeeManager;
import forestry.api.apiculture.EnumBeeChromosome;
import forestry.api.apiculture.IAlleleBeeEffect;
import forestry.api.apiculture.IAlleleBeeSpecies;
import forestry.api.apiculture.IBee;
import forestry.api.apiculture.IBeeGenome;
import forestry.api.apiculture.IBeeModifier;
import forestry.api.apiculture.IBeeMutation;
import forestry.api.apiculture.IBeeRoot;
import forestry.api.apiculture.IBeekeepingMode;
import forestry.api.apiculture.IHiveDrop;
import forestry.api.apiculture.IHiveFrame;
import forestry.api.apiculture.IJubilanceProvider;
import forestry.api.apiculture.hives.HiveManager;
import forestry.api.apiculture.hives.IHiveDescription;
import forestry.api.core.EnumHumidity;
import forestry.api.core.EnumTemperature;
import forestry.api.genetics.AlleleManager;
import forestry.api.genetics.IAllele;
import forestry.api.genetics.IAlleleArea;
import forestry.api.genetics.IAlleleBoolean;
import forestry.api.genetics.IAlleleEffect;
import forestry.api.genetics.IAlleleFloat;
import forestry.api.genetics.IAlleleFlowers;
import forestry.api.genetics.IAlleleInteger;
import forestry.api.genetics.IAlleleTolerance;
import forestry.api.genetics.IClassification;

/**
 * What the running game knows about bees: species with their climate, template genome and products, the alleles those
 * genomes are made of, mutations, hive frames and wild hive drops, written to {@code bees/*.json}. Descriptions people
 * write about bees, such as what an effect actually does, are not game data and stay on the wiki.
 */
public final class BeeExport {

    private static final java.util.regex.Pattern LANGUAGE_KEY = java.util.regex.Pattern
        .compile("[A-Za-z]\\w*(\\.[\\w ]+)+");

    private BeeExport() {}

    public static void write(File directory) throws Exception {
        IBeeRoot root = BeeManager.beeRoot;
        World world = Minecraft.getMinecraft().theWorld;
        Map<String, IAllele> used = new TreeMap<>();
        Map<String, Map<String, String>> branches = new TreeMap<>();
        List<Map<String, Object>> species = species(root, used, branches);
        List<Map<String, Object>> mutations = mutations(root);
        Files.createDirectories(directory.toPath());

        Map<String, Object> speciesFile = new LinkedHashMap<>();
        speciesFile.put("chromosomes", chromosomes());
        // Climates are ids, named once in the biome export. Branches are listed once here.
        speciesFile.put("branches", branches);
        speciesFile.put("species", species);
        save(directory, "species.json", speciesFile);

        Map<String, Object> mutationFile = new LinkedHashMap<>();
        mutationFile.put("mode", mode(root, root.getBeekeepingMode(world)));
        List<Map<String, Object>> modes = new ArrayList<>();
        for (IBeekeepingMode mode : root.getBeekeepingModes()) modes.add(mode(root, mode));
        mutationFile.put("modes", modes);
        mutationFile.put("researchCatalysts", researchCatalysts(root));
        mutationFile.put("mutations", mutations);
        save(directory, "mutations.json", mutationFile);

        for (EnumBeeChromosome chromosome : EnumBeeChromosome.values()) {
            if (chromosome == EnumBeeChromosome.SPECIES) continue;
            for (IAllele allele : AlleleManager.alleleRegistry.getRegisteredAlleles(chromosome)) {
                used.put(allele.getUID(), allele);
            }
        }
        List<Map<String, Object>> alleles = new ArrayList<>();
        for (IAllele allele : used.values()) alleles.add(allele(allele));
        save(directory, "alleles.json", singleton("alleles", alleles));
        save(directory, "frames.json", singleton("frames", frames(root)));
        save(directory, "flowers.json", singleton("flowers", flowers()));
        save(directory, "hives.json", singleton("hives", hives(root, world)));
    }

    private static Map<String, Object> singleton(String key, Object value) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(key, value);
        return map;
    }

    private static void save(File directory, String name, Object data) throws IOException {
        Files.write(
            new File(directory, name).toPath(),
            GTNHDumper.GSON.toJson(data)
                .getBytes(StandardCharsets.UTF_8));
    }

    private static List<String> chromosomes() {
        List<String> names = new ArrayList<>();
        for (EnumBeeChromosome chromosome : EnumBeeChromosome.values()) names.add(chromosome.name());
        return names;
    }

    /** A language key the game has no text for, such as a branch without a translation, is no text. */
    private static String text(String value) {
        return value == null || LANGUAGE_KEY.matcher(value.trim())
            .matches() ? null : value;
    }

    /** Records the items species produce, so that those of disabled species are exported too. */
    public static void captureItems() {
        for (IAllele allele : AlleleManager.alleleRegistry.getRegisteredAlleles(EnumBeeChromosome.SPECIES)) {
            if (!(allele instanceof IAlleleBeeSpecies bee)) continue;
            for (Map<ItemStack, Float> products : java.util.Arrays
                .asList(bee.getProductChances(), bee.getSpecialtyChances())) {
                if (products != null) products.keySet()
                    .forEach(RecipeItemVariants::capture);
            }
        }
    }

    private static String english(String uid) {
        EnglishNames names = com.iouter.gtnhdumper.automation.ExportSession.englishNames();
        return names == null ? null : names.allele(uid);
    }

    private static List<Map<String, Object>> species(IBeeRoot root, Map<String, IAllele> used,
        Map<String, Map<String, String>> branches) {
        Map<String, IAlleleBeeSpecies> registered = new TreeMap<>();
        for (IAllele allele : AlleleManager.alleleRegistry.getRegisteredAlleles(EnumBeeChromosome.SPECIES)) {
            if (allele instanceof IAlleleBeeSpecies bee) registered.put(bee.getUID(), bee);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (IAlleleBeeSpecies bee : registered.values()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("uid", bee.getUID());
            row.put("unlocalizedName", bee.getUnlocalizedName());
            row.put("name", bee.getName());
            row.put("englishName", english(bee.getUID()));
            row.put("binomial", bee.getBinomial());
            row.put("authority", bee.getAuthority());
            row.put("description", text(bee.getDescription()));
            row.put("branch", classify(bee.getBranch(), branches));
            row.put("dominant", bee.isDominant());
            row.put("secret", bee.isSecret());
            row.put("counted", bee.isCounted());
            row.put("glint", bee.hasEffect());
            row.put("blacklisted", AlleleManager.alleleRegistry.isBlacklisted(bee.getUID()));
            row.put("nocturnal", bee.isNocturnal());
            row.put("complexity", bee.getComplexity());
            row.put(
                "temperature",
                bee.getTemperature()
                    .name());
            row.put(
                "humidity",
                bee.getHumidity()
                    .name());
            row.put("colors", java.util.Arrays.asList(bee.getIconColour(0), bee.getIconColour(1)));
            // The condition under which the species yields its specialties.
            row.put("jubilance", jubilance(bee));
            row.put("products", chances(bee.getProductChances()));
            row.put("specialties", chances(bee.getSpecialtyChances()));
            IAllele[] template = root.getTemplate(bee.getUID());
            if (template != null) {
                Map<String, String> genome = new LinkedHashMap<>();
                for (EnumBeeChromosome chromosome : EnumBeeChromosome.values()) {
                    int index = chromosome.ordinal();
                    if (chromosome == EnumBeeChromosome.SPECIES || index >= template.length || template[index] == null)
                        continue;
                    genome.put(chromosome.name(), template[index].getUID());
                    used.put(template[index].getUID(), template[index]);
                }
                row.put("genome", genome);
            }
            rows.add(row);
        }
        return rows;
    }

    /** Adds a branch and its ancestors to the table and returns its id. */
    private static String classify(IClassification branch, Map<String, Map<String, String>> branches) {
        for (IClassification node = branch; node != null; node = node.getParent()) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("level", String.valueOf(node.getLevel()));
            row.put("name", text(node.getName()));
            row.put("scientific", node.getScientific());
            row.put("description", text(node.getDescription()));
            if (node.getParent() != null) row.put(
                "parent",
                node.getParent()
                    .getUID());
            if (branches.putIfAbsent(node.getUID(), row) != null) break;
        }
        return branch == null ? null : branch.getUID();
    }

    private static Map<String, String> jubilance(IAlleleBeeSpecies bee) {
        try {
            Object provider = bee.getClass()
                .getMethod("getJubilanceProvider")
                .invoke(bee);
            if (!(provider instanceof IJubilanceProvider jubilance)) return null;
            Map<String, String> row = new LinkedHashMap<>();
            row.put(
                "provider",
                provider.getClass()
                    .getName());
            row.put("description", text(jubilance.getDescription()));
            return row;
        } catch (NoSuchMethodException notAForestrySpecies) {
            return null;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot read the jubilance of " + bee.getUID(), error);
        }
    }

    /** Products with their base chance (1 = every cycle at base production), ordered by chance and item. */
    private static List<Map<String, Object>> chances(Map<ItemStack, Float> products) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (products == null) return rows;
        for (Map.Entry<ItemStack, Float> entry : products.entrySet()) {
            Map<String, Object> row = stack(entry.getKey());
            if (row == null) continue;
            row.put("chance", entry.getValue());
            rows.add(row);
        }
        rows.sort(
            Comparator.<Map<String, Object>>comparingDouble(row -> -(Float) row.get("chance"))
                .thenComparing(row -> (String) row.get("item")));
        return rows;
    }

    private static Map<String, Object> stack(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return null;
        RecipeItemVariants.capture(stack);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("item", Utils.getItemStackShortKey(stack));
        row.put("count", stack.stackSize);
        return row;
    }

    private static Map<String, Object> allele(IAllele allele) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("uid", allele.getUID());
        row.put("unlocalizedName", allele.getUnlocalizedName());
        row.put("name", text(allele.getName()));
        row.put("englishName", text(english(allele.getUID())));
        row.put("dominant", allele.isDominant());
        List<String> chromosomes = new ArrayList<>();
        for (EnumBeeChromosome chromosome : EnumBeeChromosome.values()) {
            if (AlleleManager.alleleRegistry.getRegisteredAlleles(chromosome)
                .contains(allele)) chromosomes.add(chromosome.name());
        }
        row.put("chromosomes", chromosomes);
        if (allele instanceof IAlleleFloat value) row.put("value", value.getValue());
        else if (allele instanceof IAlleleInteger value) row.put("value", value.getValue());
        else if (allele instanceof IAlleleArea value) row.put("value", value.getValue());
        else if (allele instanceof IAlleleBoolean value) row.put("value", value.getValue());
        else if (allele instanceof IAlleleTolerance value) row.put(
            "value",
            value.getValue()
                .name());
        else if (allele instanceof IAlleleFlowers flowers) {
            row.put(
                "value",
                flowers.getProvider()
                    .getDescription());
            row.put(
                "flowerType",
                flowers.getProvider()
                    .getFlowerType());
        }
        if (allele instanceof IAlleleBeeEffect effect) row.put("combinable", ((IAlleleEffect) effect).isCombinable());
        return row;
    }

    /** The world's beekeeping mode and how it scales the base numbers (GTNH plays in hard mode). */
    private static Map<String, Object> mode(IBeeRoot root, IBeekeepingMode mode) {
        IBeeModifier modifier = mode.getBeeModifier();
        IBeeGenome genome = root.templateAsGenome(root.getDefaultTemplate());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", mode.getName());
        // The mode describes itself with language keys.
        List<String> description = new ArrayList<>();
        for (String key : mode.getDescription()) {
            description.add(text(net.minecraft.util.StatCollector.translateToLocalFormatted("for." + key)));
        }
        row.put("description", description);
        row.put("mutationModifier", modifier.getMutationModifier(genome, genome, 1f));
        row.put("lifespanModifier", modifier.getLifespanModifier(genome, genome, 1f));
        row.put("productionModifier", modifier.getProductionModifier(genome, 1f));
        return row;
    }

    /** Items that raise the chance of a research result, whatever the species. */
    private static List<Map<String, Object>> researchCatalysts(IBeeRoot root) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<ItemStack, Float> entry : root.getResearchCatalysts()
            .entrySet()) {
            Map<String, Object> row = stack(entry.getKey());
            if (row == null) continue;
            row.put("suitability", entry.getValue());
            rows.add(row);
        }
        rows.sort(Comparator.comparing((Map<String, Object> row) -> (String) row.get("item")));
        return rows;
    }

    private static List<Map<String, Object>> mutations(IBeeRoot root) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (IBeeMutation mutation : root.getMutations(false)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put(
                "parents",
                Arrays.asList(
                    mutation.getAllele0()
                        .getUID(),
                    mutation.getAllele1()
                        .getUID()));
            row.put("result", mutation.getTemplate()[EnumBeeChromosome.SPECIES.ordinal()].getUID());
            // Percent, before the beekeeping mode and any housing modifiers.
            row.put("chance", mutation.getBaseChance());
            row.put("conditions", new ArrayList<>(mutation.getSpecialConditions()));
            row.put("secret", mutation.isSecret());
            rows.add(row);
        }
        rows.sort(
            Comparator.comparing((Map<String, Object> row) -> (String) row.get("result"))
                .thenComparing(row -> String.valueOf(row.get("parents")))
                .thenComparing(row -> String.valueOf(row.get("conditions"))));
        return rows;
    }

    /** Every hive frame with the factors it applies to a plain bee in an otherwise unmodified housing. */
    private static List<Map<String, Object>> frames(IBeeRoot root) {
        IBeeGenome genome = root.templateAsGenome(root.getDefaultTemplate());
        Map<String, Map<String, Object>> rows = new TreeMap<>();
        for (ItemStack stack : AllItemStacks.getFilteredItemStacks()) {
            if (stack == null || !(stack.getItem() instanceof IHiveFrame frame)) continue;
            IBeeModifier modifier = frame.getBeeModifier();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("item", Utils.getItemStackShortKey(stack));
            row.put("maxDamage", stack.getMaxDamage());
            row.put("territory", modifier.getTerritoryModifier(genome, 1f));
            row.put("mutation", modifier.getMutationModifier(genome, genome, 1f));
            row.put("lifespan", modifier.getLifespanModifier(genome, genome, 1f));
            row.put("production", modifier.getProductionModifier(genome, 1f));
            row.put("flowering", modifier.getFloweringModifier(genome, 1f));
            row.put("geneticDecay", modifier.getGeneticDecay(genome, 1f));
            row.put("sealed", modifier.isSealed());
            row.put("selfLighted", modifier.isSelfLighted());
            row.put("sunlightSimulated", modifier.isSunlightSimulated());
            row.put("hellish", modifier.isHellish());
            row.put("selfUnlighted", modifier.isSelfUnlighted());
            row.put("tooltip", frame.getFrameTooltip());
            rows.putIfAbsent((String) row.get("item"), row);
        }
        return new ArrayList<>(rows.values());
    }

    /**
     * The blocks each flower type accepts, from Forestry's flower registry. Some types also accept blocks through code
     * rules, which cannot be listed; those rules are named.
     */
    private static List<Map<String, Object>> flowers() throws ReflectiveOperationException {
        Object registry = forestry.api.apiculture.FlowerManager.flowerRegistry;
        com.google.common.collect.Multimap<?, ?> flowers = (com.google.common.collect.Multimap<?, ?>) fieldValue(
            registry,
            "registeredFlowers");
        com.google.common.collect.Multimap<?, ?> rules = (com.google.common.collect.Multimap<?, ?>) fieldValue(
            registry,
            "registeredRules");
        java.util.Set<String> types = new java.util.TreeSet<>();
        for (Object type : flowers.keySet()) types.add((String) type);
        for (Object type : rules.keySet()) types.add((String) type);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String type : types) {
            Map<String, Map<String, Object>> blocks = new TreeMap<>();
            for (Map.Entry<?, ?> entry : flowers.entries()) {
                if (!type.equals(entry.getKey())) continue;
                Object flower = entry.getValue();
                Block block = (Block) fieldValue(flower, "block");
                int meta = (Integer) fieldValue(flower, "meta");
                String name = GameData.getBlockRegistry()
                    .getNameForObject(block);
                if (name == null) continue;
                boolean any = meta == net.minecraftforge.oredict.OreDictionary.WILDCARD_VALUE;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("block", name);
                // Absent: any metadata counts.
                if (!any) row.put("meta", meta);
                if (Item.getItemFromBlock(block) != null) {
                    row.put("item", Utils.getItemStackShortKey(new ItemStack(block, 1, any ? 0 : meta)));
                }
                // Above 0: bees may also plant it.
                double weight = ((Number) fieldValue(flower, "weight")).doubleValue();
                row.put("plantWeight", weight);
                String key = name + ":" + (any ? "*" : meta);
                Map<String, Object> previous = blocks.get(key);
                if (previous == null || (Double) previous.get("plantWeight") < weight) blocks.put(key, row);
            }
            List<String> codeRules = new ArrayList<>();
            for (Map.Entry<?, ?> entry : rules.entries()) {
                if (!type.equals(entry.getKey())) continue;
                String name = entry.getValue()
                    .getClass()
                    .getName();
                // A lambda's name ends in a number that differs between runs.
                int lambda = name.indexOf("$$Lambda");
                codeRules.add(lambda < 0 ? name : name.substring(0, lambda));
            }
            codeRules.sort(Comparator.naturalOrder());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", type);
            row.put("blocks", new ArrayList<>(blocks.values()));
            row.put("codeRules", codeRules);
            rows.add(row);
        }
        return rows;
    }

    private static Object fieldValue(Object owner, String name) throws ReflectiveOperationException {
        for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(owner);
            } catch (NoSuchFieldException inherited) {
                // Declared further up.
            }
        }
        throw new NoSuchFieldException(
            owner.getClass()
                .getName() + "."
                + name);
    }

    /**
     * What breaking a wild hive can drop. Forestry keeps its hives in a registry; Magic Bees and Extra Bees keep their
     * drop lists on enums that their hive blocks read.
     */
    private static List<Map<String, Object>> hives(IBeeRoot root, World world) throws ReflectiveOperationException {
        List<Map<String, Object>> rows = new ArrayList<>();
        Object registry = HiveManager.hiveRegistry;
        Field hivesField = registry.getClass()
            .getDeclaredField("hives");
        hivesField.setAccessible(true);
        Map<String, Object> registered = new TreeMap<>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) hivesField.get(registry)).entrySet()) {
            registered.put((String) entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, Object> entry : registered.entrySet()) {
            Object hive = entry.getValue();
            Block block = (Block) hive.getClass()
                .getMethod("getHiveBlock")
                .invoke(hive);
            int meta = (Integer) hive.getClass()
                .getMethod("getHiveMeta")
                .invoke(hive);
            Field description = hive.getClass()
                .getDeclaredField("hiveDescription");
            description.setAccessible(true);
            Map<String, Object> row = hive("forestry", entry.getKey(), block, meta);
            row.put(
                "drops",
                drops(
                    root,
                    world,
                    (Collection<?>) hive.getClass()
                        .getMethod("getDrops")
                        .invoke(hive)));
            row.put("generation", generation((IHiveDescription) description.get(hive)));
            rows.add(row);
        }
        enumHives(rows, root, world, "magicbees", "magicbees.block.types.HiveType", "MagicBees:magicbees.hive");
        enumHives(rows, root, world, "extrabees", "binnie.extrabees.worldgen.EnumHiveType", "ExtraBees:hive");
        return rows;
    }

    private static void enumHives(List<Map<String, Object>> rows, IBeeRoot root, World world, String source,
        String typeName, String blockName) throws ReflectiveOperationException {
        Class<?> type;
        try {
            type = Class.forName(typeName);
        } catch (ClassNotFoundException missingMod) {
            return;
        }
        Field drops = type.getDeclaredField("drops");
        drops.setAccessible(true);
        Block block = findBlock(blockName);
        for (Object constant : type.getEnumConstants()) {
            Enum<?> value = (Enum<?>) constant;
            Map<String, Object> row = hive(source, value.name(), block, value.ordinal());
            row.put("drops", drops(root, world, (Collection<?>) drops.get(value)));
            rows.add(row);
        }
    }

    /** The hive block of a mod whose blocks are registered under names that differ between its versions. */
    private static Block findBlock(String expected) {
        Object registered = GameData.getBlockRegistry()
            .getObject(expected);
        if (registered instanceof Block block && block != net.minecraft.init.Blocks.air) return block;
        String namespace = expected.substring(0, expected.indexOf(':') + 1);
        for (Object key : GameData.getBlockRegistry()
            .getKeys()) {
            String name = (String) key;
            if (name.startsWith(namespace) && name.toLowerCase(java.util.Locale.ROOT)
                .contains("hive")
                && !name.toLowerCase(java.util.Locale.ROOT)
                    .contains("frame"))
                return (Block) GameData.getBlockRegistry()
                    .getObject(name);
        }
        return null;
    }

    private static Map<String, Object> hive(String source, String id, Block block, int meta) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("source", source);
        row.put("id", id);
        if (block != null && Item.getItemFromBlock(block) != null) {
            row.put("item", Utils.getItemStackShortKey(new ItemStack(block, 1, meta)));
        }
        return row;
    }

    private static List<Map<String, Object>> drops(IBeeRoot root, World world, Collection<?> drops)
        throws ReflectiveOperationException {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object value : drops) {
            IHiveDrop drop = (IHiveDrop) value;
            Map<String, Object> row = new LinkedHashMap<>();
            // The drone always carries the drop's genome; the princess is rolled to be ignoble or not.
            for (ItemStack drone : drop.getDrones(world, 0, 0, 0, 0)) {
                IBee bee = root.getMember(drone);
                if (bee != null) {
                    row.put(
                        "species",
                        bee.getGenome()
                            .getPrimary()
                            .getUID());
                    break;
                }
            }
            // Weight among the hive's drops, not a percentage.
            row.put("weight", drop.getChance(world, 0, 0, 0));
            for (String name : new String[] { "ignobleShare", "ignoblePercent" }) {
                try {
                    Field share = drop.getClass()
                        .getDeclaredField(name);
                    share.setAccessible(true);
                    row.put("ignobleShare", share.get(drop));
                } catch (NoSuchFieldException otherImplementation) {
                    // Only some drop types have ignoble princesses.
                }
            }
            List<Map<String, Object>> additional = new ArrayList<>();
            for (ItemStack extra : drop.getAdditional(world, 0, 0, 0, 0)) {
                Map<String, Object> item = stack(extra);
                if (item != null) additional.add(item);
            }
            row.put("additional", additional);
            rows.add(row);
        }
        return rows;
    }

    /** Where world generation places a registered hive: its chance and the biomes whose climate it accepts. */
    private static Map<String, Object> generation(IHiveDescription description) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("chance", description.getGenChance());
        List<Integer> biomes = new ArrayList<>();
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (BiomeGenBase biome : BiomeGenBase.getBiomeGenArray()) {
            if (biome == null || !seen.add(biome.biomeID)) continue;
            if (description.isGoodBiome(biome)
                && description.isGoodTemperature(EnumTemperature.getFromValue(biome.temperature))
                && description.isGoodHumidity(EnumHumidity.getFromValue(biome.rainfall))) biomes.add(biome.biomeID);
        }
        row.put("biomes", biomes);
        return row;
    }
}
