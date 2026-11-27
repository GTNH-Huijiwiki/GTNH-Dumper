package com.iouter.gtnhdumper.common.dumper;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.opengl.GL11;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.iouter.gtnhdumper.CommonProxy;
import com.iouter.gtnhdumper.GTNHDumper;
import com.iouter.gtnhdumper.automation.ExportSession;
import com.iouter.gtnhdumper.common.base.WikiDumper;
import com.iouter.gtnhdumper.common.utils.AllItemStacks;
import com.iouter.gtnhdumper.common.utils.DynamicTexture;
import com.iouter.gtnhdumper.common.utils.FBOHelper;
import com.iouter.gtnhdumper.common.utils.IconHalo;
import com.iouter.gtnhdumper.common.utils.IconImageFormat;
import com.iouter.gtnhdumper.common.utils.IconRenderClock;
import com.iouter.gtnhdumper.common.utils.ModFilter;
import com.iouter.gtnhdumper.common.utils.NameSelection;
import com.iouter.gtnhdumper.common.utils.PngWriter;
import com.iouter.gtnhdumper.common.utils.RecipeItemVariants;
import com.iouter.gtnhdumper.common.utils.Utils;

import bartworks.system.material.BWMetaGeneratedOres;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.shadow.org.apache.commons.csv.CSVFormat;
import codechicken.nei.shadow.org.apache.commons.csv.CSVPrinter;
import cpw.mods.fml.client.FMLClientHandler;
import cpw.mods.fml.common.registry.GameData;
import gregtech.common.blocks.GTBlockOre;
import gtPlusPlus.core.block.base.BlockBaseOre;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

public class ItemIconDumper extends WikiDumper {

    private static final int SINGLE_FRAME_SIZE = 256;
    /** Animation frame size for icons that cannot be drawn at their texture resolution without loss. */
    private static final int DETAIL_FRAME_SIZE = 128;
    private static final long MAX_STRIP_WIDTH = 65536;
    /**
     * The star field shader derives star size from the eye-space position of each vertex, so those icons must be drawn
     * as far from the camera as the real inventory screen draws them; everything else keeps the original setup.
     */
    private static boolean screenDistance;
    /** The condensate overlay frame every render shows; only its animation steps it. */
    private static int condensateFrame;
    /** The star field's shapes are fine enough to need the full still size; 64 px frames turn them into blocks. */
    private static final int STARFIELD_FRAME_SIZE = 256;
    /** Static icon file name to whether it, or its animation, is pixel art; written to itemicon.json. */
    private static final Map<String, Boolean> pixelStills = new HashMap<>();
    private static final Map<String, Boolean> pixelAnimations = new HashMap<>();
    /** Static icon file name to the halo left out of it, as {@code texture|aarrggbb|spread}; see {@link IconHalo}. */
    private static final Map<String, String> halos = new HashMap<>();
    /** Animations rendered above their texture resolution: file name to "texture->frame" sizes. */
    private static final Map<String, String> detailAnimations = new LinkedHashMap<>();
    private static final Object2IntOpenHashMap<ItemStack> frameCountMap = new Object2IntOpenHashMap<>();
    private static final Object2IntOpenHashMap<ItemStack> dynamicSizeMap = new Object2IntOpenHashMap<>();
    private static final Map<Integer, FBOHelper> fbos = new HashMap<>();
    /** Animations whose frame strip exceeds gtnhdumper.maxAnimationMB; exported as still images only. */
    private static final Map<String, String> OVERSIZED_ANIMATIONS = new LinkedHashMap<>();
    private static JsonObject resume;
    private static int resumedIcons;
    private static final Map<String, Integer> unregisteredItems = new LinkedHashMap<>();
    private static final Map<String, String> renderFailures = new LinkedHashMap<>();

    /** The dumper whose export is running; renders resolve their output directory through it. */
    private static ItemIconDumper current;
    private List<ItemStack> selectedStacks;
    private File imageOutput;
    private final Map<String, Object> captureReport = new LinkedHashMap<>();

    private File outputFile(String path) {
        return imageOutput == null ? ModFilter.current()
            .outputFile(path) : new File(imageOutput, path);
    }

    /** Selects the stacks before any output is written, so a mistyped selection fails the export at once. */
    private void begin() {
        captureReport.clear();
        imageOutput = ModFilter.current()
            .outputFile("");
        NameSelection keys = new NameSelection(ExportSession.imageKeys());
        selectedStacks = new ArrayList<>();
        for (ItemStack stack : AllItemStacks.getFilteredItemStacks()) {
            if (keys.matches(Utils.getItemStackShortKey(stack))) selectedStacks.add(stack);
        }
        if (!keys.unmatched()
            .isEmpty()) throw new IllegalArgumentException("Unknown imageKeys: " + keys.unmatched());
        current = this;
    }

    private void end() {
        for (FBOHelper fbo : fbos.values()) fbo.close();
        fbos.clear();
        current = null;
        selectedStacks = null;
        imageOutput = null;
    }

    @Override
    public void dumpFile() {
        if (!canDump()) return;
        final File file = ModFilter.current()
            .outputFile("itemicon.json");
        try {
            Files.createDirectories(
                file.toPath()
                    .getParent());
            dumpTo(file, getMode());
            NEIClientUtils.printChatMessage(dumpMessage(file));
        } catch (Exception | LinkageError error) {
            GTNHDumper.LOG.error("Item icon export failed", error);
        }
    }

    public ItemIconDumper() {
        super("tools.dump.gtnhdumper.itemicon");
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
        return "shortKey";
    }

    @Override
    public Iterable<Object[]> dumpObject(int mode) {
        begin();
        try {
            return exportIcons(mode);
        } finally {
            end();
        }
    }

    private Iterable<Object[]> exportIcons(int mode) {
        long started = System.nanoTime();
        int workers = Integer.getInteger("gtnhdumper.pngWorkers", PngWriter.defaultWorkers());
        // Content-addressed PNG cache shared by all runs of this instance; unchanged pixels are linked, not encoded.
        File cache = ExportSession.pngCache();
        PngWriter writer = new PngWriter(workers, cache);
        synchronized (OVERSIZED_ANIMATIONS) {
            OVERSIZED_ANIMATIONS.clear();
        }
        resumedIcons = 0;
        unregisteredItems.clear();
        renderFailures.clear();
        detailAnimations.clear();
        resume = null;
        File resumeFile = ExportSession.imageResume();
        if (resumeFile != null) {
            try {
                resume = GTNHDumper.GSON.fromJson(
                    new String(Files.readAllBytes(resumeFile.toPath()), java.nio.charset.StandardCharsets.UTF_8),
                    JsonObject.class);
            } catch (IOException error) {
                throw new IllegalStateException("Could not load resume image manifest", error);
            }
        }
        FBOHelper.setPngWriter(writer);
        IconRenderClock.freeze();
        try (writer) {
            return dumpIcons(mode);
        } finally {
            IconRenderClock.release();
            FBOHelper.setPngWriter(null);
            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("workers", workers);
            metrics.put("maximumCompression", true);
            metrics.put("iconFormat", ExportSession.legacyPng() ? "png" : "webp");
            metrics.put("lossyBlockStills", IconImageFormat.lossyKept());
            metrics.put("writtenImages", writer.count());
            metrics.put("pngCache", cache == null ? null : cache.getAbsolutePath());
            metrics.put("pngCacheHits", writer.cacheHits());
            metrics.put("frozenRenderClock", IconRenderClock.report());
            metrics.put("resumedIcons", resumedIcons);
            metrics.put("excludedUnregisteredItems", unregisteredItems);
            metrics.put("renderFailures", renderFailures.size());
            metrics.put("detailAnimations", new LinkedHashMap<>(detailAnimations));
            synchronized (OVERSIZED_ANIMATIONS) {
                metrics.put("oversizedAnimations", new LinkedHashMap<>(OVERSIZED_ANIMATIONS));
            }
            metrics.put("wallNanos", System.nanoTime() - started);
            metrics.put("encodingAndWriteTaskNanos", writer.encodeNanos());
            try {
                Files.write(
                    outputFile("icon-capture-report.json").toPath(),
                    GTNHDumper.GSON.toJson(captureReport)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                // Pixel hash per written file: identical hashes mean identical pixels, independent of PNG encoding.
                Map<String, Object> hashes = new LinkedHashMap<>();
                hashes.put("schemaVersion", 1);
                hashes.put("algorithm", "sha1(width:height:ARGB big-endian rows)");
                hashes.put("images", writer.pixelHashes());
                Files.write(
                    outputFile("icon-failures.json").toPath(),
                    GTNHDumper.GSON.toJson(renderFailures)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                Files.write(
                    outputFile("icon-hashes.json").toPath(),
                    GTNHDumper.GSON.toJson(hashes)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                Files.write(
                    outputFile("icon-performance.json").toPath(),
                    GTNHDumper.GSON.toJson(metrics)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } catch (IOException error) {
                throw new IllegalStateException("Could not record icon export metrics", error);
            }
        }
    }

    private Iterable<Object[]> dumpIcons(int mode) {
        com.iouter.gtnhdumper.common.utils.TrophyRenderCompat.useImmediateRenderer();
        LinkedList<Object[]> list = new LinkedList<>();
        frameCountMap.clear();
        dynamicSizeMap.clear();
        pixelStills.clear();
        pixelAnimations.clear();
        halos.clear();
        IconImageFormat.reset();

        List<ItemStack> itemStacks = selectedStacks;

        Map<String, String> redirectMap = new LinkedHashMap<>();
        Set<String> itemNameSet = new HashSet<>();
        Set<String> baseIconKeys = new HashSet<>();
        Map<String, ItemStack> rendered = new HashMap<>();
        Map<String, JsonObject> reference = loadReferenceMetadata();
        Map<String, String> missingReference = new LinkedHashMap<>();
        int visited = 0;

        for (ItemStack stack : itemStacks) {
            if (++visited % 1000 == 0) ExportSession.progress(
                "itemicon: " + visited + "/" + itemStacks.size() + " stacks; " + rendered.size() + " distinct icons");
            if (Utils.isStackInvalid(stack)) continue;
            // AdvItemPanelDumper also excludes unregistered display-only objects.
            // They have no stable registry key and would otherwise overwrite icon_null.png.
            if (GameData.getItemRegistry()
                .getNameForObject(stack.getItem()) == null) {
                String identity = stack.getItem()
                    .getClass()
                    .getName() + ":"
                    + stack.getItemDamage();
                unregisteredItems.merge(identity, 1, Integer::sum);
                continue;
            }
            if (usesBaseIcon(stack)) {
                stack = getBaseIconStack(stack);
                if (!baseIconKeys.add(Utils.getItemStackShortKey(stack))) continue;
            }
            String iconName = getIconFileName(stack);
            ItemStack previous = rendered.putIfAbsent(iconName, stack);
            if (previous == null) {
                if (reference == null) {
                    // NBT copies may serialize map entries in a different order. Keep the original identity for
                    // filenames while rendering a copy.
                    ItemStack renderStack = GtFluidDisplayTag.forRender(stack.copy());
                    try {
                        renderGeneralItem(
                            renderStack,
                            getFbo(SINGLE_FRAME_SIZE),
                            RenderItem.getInstance(),
                            iconName,
                            getIconFileName(stack, true));
                    } catch (RuntimeException | LinkageError error) {
                        if (error instanceof PngWriter.OutputFailure) throw error;
                        // A single item whose own icon code rejects its NBT must not abort the export.
                        // It gets no icon mapping; the failure is listed in icon-failures.json.
                        renderFailures.put(Utils.getItemStackShortKey(stack), error.toString());
                        rendered.remove(iconName);
                        continue;
                    }
                    if (frameCountMap.containsKey(renderStack))
                        frameCountMap.put(stack, frameCountMap.getInt(renderStack));
                    if (dynamicSizeMap.containsKey(renderStack))
                        dynamicSizeMap.put(stack, dynamicSizeMap.getInt(renderStack));
                } else {
                    JsonObject metadata = reference.get(Utils.getItemStackShortKey(stack));
                    if (metadata == null || !iconName.equals(
                        metadata.get("iconName")
                            .getAsString())) {
                        missingReference.put(Utils.getItemStackShortKey(stack), iconName);
                        continue;
                    }
                    if (metadata.has("frameCount") && !metadata.get("frameCount")
                        .isJsonNull()) frameCountMap.put(
                            stack,
                            metadata.get("frameCount")
                                .getAsInt());
                    if (metadata.has("size") && !metadata.get("size")
                        .isJsonNull()) dynamicSizeMap.put(
                            stack,
                            metadata.get("size")
                                .getAsInt());
                    if (metadata.has("iconHalo") && !metadata.get("iconHalo")
                        .isJsonNull()) halos.put(
                            iconName,
                            metadata.get("iconHalo")
                                .getAsString());
                }
            } else {
                if (!new RecipeItemVariants.Key(previous).equals(new RecipeItemVariants.Key(stack)))
                    throw new IllegalStateException("Different item identities share icon filename: " + iconName);
                if (frameCountMap.containsKey(previous)) frameCountMap.put(stack, frameCountMap.getInt(previous));
                if (dynamicSizeMap.containsKey(previous)) dynamicSizeMap.put(stack, dynamicSizeMap.getInt(previous));
            }
            final String translatedName = EnumChatFormatting
                .getTextWithoutFormattingCodes(GuiContainerManager.itemDisplayNameShort(stack.copy()));
            final String imageName = getIconFileName(stack);
            final String vaildFileName = Utils.replaceIllegalChars(translatedName);
            if (itemNameSet.add(vaildFileName)) {
                redirectMap.put("File:" + vaildFileName + iconExtension(), "File:" + imageName);
            } else {
                int i = 2;
                while (!itemNameSet.add(vaildFileName + " " + i)) {
                    i++;
                }
                redirectMap.put("File:" + vaildFileName + " " + i + iconExtension(), "File:" + imageName);
            }
            list.add(
                new Object[] { Utils.getItemStackShortKey(stack), imageName, getItemDynamicSize(stack),
                    getItemFrameCount(stack), pixelStills.get(imageName), pixelAnimations.get(imageName),
                    halos.get(imageName) });
        }

        if (reference != null) {
            try {
                Files.write(
                    outputFile("image-mapping-diff.json").toPath(),
                    GTNHDumper.GSON.toJson(missingReference)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } catch (IOException error) {
                throw new IllegalStateException("Could not write image mapping comparison", error);
            }
            if (!missingReference.isEmpty()) throw new IllegalStateException(
                missingReference.size() + " new/changed icon mappings; see image-mapping-diff.json");
        }

        try (CSVPrinter printer = new CSVPrinter(
            Files.newBufferedWriter(outputFile("image_redirect.csv").toPath()),
            CSVFormat.DEFAULT)) {
            for (Map.Entry<String, String> e : redirectMap.entrySet()) {
                printer.printRecord(e.getKey(), e.getValue());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return list;
    }

    private static Map<String, JsonObject> loadReferenceMetadata() {
        File file = ExportSession.imageMetadata();
        if (file == null) return null;
        try {
            JsonObject data = GTNHDumper.GSON.fromJson(
                new String(Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8),
                JsonObject.class);
            Map<String, JsonObject> reference = new HashMap<>();
            for (JsonElement element : data.getAsJsonArray("shortKey")) {
                JsonObject row = element.getAsJsonObject();
                String key = row.get("shortKey")
                    .getAsString();
                JsonObject previous = reference.putIfAbsent(key, row);
                if (previous != null && !previous.equals(row))
                    throw new IllegalStateException("Conflicting reference image metadata: " + key);
            }
            return reference;
        } catch (IOException error) {
            throw new IllegalStateException("Could not load reference image metadata", error);
        }
    }

    @Override
    public String[] header() {
        return new String[] { "shortKey", "iconName", "size", "frameCount", "iconPixelated", "animationPixelated",
            "iconHalo" };
    }

    @Override
    public ChatComponentTranslation dumpMessage(File file) {
        return new ChatComponentTranslation("nei.options.tools.dump.gtnhdumper.itemicon.dumped", file.getPath());
    }

    public static String getIconFileName(ItemStack stack) {
        return getIconFileName(stack, false);
    }

    public static String getIconFileName(ItemStack stack, boolean isDynamic) {
        return "icon_" + Utils.replaceHuijiIllegalChars(Utils.getItemStackShortKey(getBaseIconStack(stack)))
            + (isDynamic ? "_dynamic" : "")
            + iconExtension();
    }

    public static void testHashNBT() {
        Map<String, String> hashMap = new HashMap<>();
        int collisionCount = 0;
        List<ItemStack> itemStacks = AllItemStacks.getAllItemStacks();

        for (ItemStack stack : itemStacks) {
            String nbt = Utils.getItemNBT(stack);
            String hashNBT = Utils.hashNBT(stack);
            if (hashNBT == null) continue;
            String temp = hashMap.get(nbt);
            if (temp == null) {
                hashMap.put(nbt, hashNBT);
            } else if (!hashNBT.equals(temp)) {
                collisionCount++;
                GTNHDumper.error("冲突发生在" + nbt + "，对应编码为：" + hashNBT);
            }
        }

        GTNHDumper.debug("冲突数量: " + collisionCount);
        GTNHDumper.debug("唯一率: " + (100 - (collisionCount * 100.0 / Math.max(1, itemStacks.size()))) + "%");
    }

    /** WebP unless the request asks for the legacy PNG icons. */
    private static String iconExtension() {
        return ExportSession.legacyPng() ? ".png" : IconImageFormat.EXTENSION;
    }

    private static boolean usesBaseIcon(ItemStack stack) {
        String registryName = GameData.getItemRegistry()
            .getNameForObject(stack.getItem());
        return "gregtech:gt.Volumetric_Flask".equals(registryName)
            || "gregtech:gt.Volumetric_Flask_8k".equals(registryName)
            || "gregtech:gt.Volumetric_Flask_32k".equals(registryName)
            || "gregtech:gt.Volumetric_Flask_Infinite".equals(registryName)
            || "miscutils:gt.Volumetric_Flask_8k".equals(registryName)
            || "miscutils:gt.Volumetric_Flask_32k".equals(registryName)
            || "miscutils:gt.Volumetric_Flask_Infinite".equals(registryName)
            || "appliedenergistics2:item.ItemFacade".equals(registryName);
    }

    private static ItemStack getBaseIconStack(ItemStack stack) {
        if (!usesBaseIcon(stack) || !stack.hasTagCompound()) return stack;
        // Keep the shared item stacks intact for item, recipe and ore dictionary exports.
        ItemStack baseStack = stack.copy();
        baseStack.setTagCompound(null);
        return baseStack;
    }

    public static void prepareRenderItem(ItemStack itemStack, RenderItem itemRenderer) {
        renderGeneralItem(itemStack, getFbo(SINGLE_FRAME_SIZE), itemRenderer);
    }

    private static File iconOutputFile(String path) {
        return current == null ? ModFilter.current()
            .outputFile(path) : current.outputFile(path);
    }

    private static FBOHelper getFbo(int size) {
        FBOHelper fbo = fbos.get(size);
        if (fbo == null) {
            fbo = new FBOHelper(size);
            fbos.put(size, fbo);
        }
        return fbo;
    }

    /**
     * Created by Jerrell Fang on 2/23/2015.
     *
     * @author Meow J
     *         <p>
     *         Borrowed from <a href=
     *         "https://github.com/Snownee/Item-Render-Dark/blob/master/src/main/java/itemrender/rendering/Renderer.java">Item-Render-Rebirth</a>
     */
    private static Map<String, Object> glDiagnostic() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("activeTexture", GL11.glGetInteger(org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE));
        state.put("textureEnabled", GLStateManager.glIsEnabled(GL11.GL_TEXTURE_2D));
        state.put("textureBinding", GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D));
        state.put("program", GL11.glGetInteger(org.lwjgl.opengl.GL20.GL_CURRENT_PROGRAM));
        for (int matrix : new int[] { GL11.GL_MODELVIEW_MATRIX, GL11.GL_PROJECTION_MATRIX }) {
            java.nio.FloatBuffer values = org.lwjgl.BufferUtils.createFloatBuffer(16);
            GL11.glGetFloat(matrix, values);
            float[] copy = new float[16];
            values.get(copy);
            state.put(matrix == GL11.GL_MODELVIEW_MATRIX ? "modelView" : "projection", copy);
        }
        state.put("cull", GL11.glIsEnabled(GL11.GL_CULL_FACE));
        state.put("cullFace", GL11.glGetInteger(GL11.GL_CULL_FACE_MODE));
        state.put("lineWidth", GL11.glGetFloat(GL11.GL_LINE_WIDTH));
        state.put("depthMask", GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK));
        state.put("depthTest", GL11.glIsEnabled(GL11.GL_DEPTH_TEST));
        state.put("depthFunc", GL11.glGetInteger(GL11.GL_DEPTH_FUNC));
        state.put("clearDepth", GL11.glGetFloat(GL11.GL_DEPTH_CLEAR_VALUE));
        state.put("scissor", GL11.glIsEnabled(GL11.GL_SCISSOR_TEST));
        state.put("alphaTest", GL11.glIsEnabled(GL11.GL_ALPHA_TEST));
        state.put("alphaFunc", GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC));
        state.put("alphaRef", GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF));
        state.put("blend", GL11.glIsEnabled(GL11.GL_BLEND));
        state.put("blendSource", GL11.glGetInteger(GL11.GL_BLEND_SRC));
        state.put("blendDestination", GL11.glGetInteger(GL11.GL_BLEND_DST));
        state.put("framebuffer", GL11.glGetInteger(org.lwjgl.opengl.EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT));
        java.nio.IntBuffer viewport = org.lwjgl.BufferUtils.createIntBuffer(16);
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
        state.put("viewport", new int[] { viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3) });
        java.nio.ByteBuffer mask = org.lwjgl.BufferUtils.createByteBuffer(16);
        GL11.glGetBoolean(GL11.GL_COLOR_WRITEMASK, mask);
        state
            .put("colorMask", new boolean[] { mask.get(0) != 0, mask.get(1) != 0, mask.get(2) != 0, mask.get(3) != 0 });
        return state;
    }

    public static BufferedImage renderItem(ItemStack itemStack, FBOHelper fbo, RenderItem itemRenderer, float scale,
        DynamicTexture dynamicTexture) {
        Minecraft minecraft = FMLClientHandler.instance()
            .getClient();
        IconRenderClock.reseedSharedRandom();
        GtCondensateOverlay.show(condensateFrame);
        Map<String, Object> beforeFbo = ExportSession.imageDiagnostics() ? glDiagnostic() : null;
        fbo.begin();
        Map<String, Object> insideFbo = ExportSession.imageDiagnostics() ? glDiagnostic() : null;
        boolean restoreCull = ItemIconRenderProfile.forStack(itemStack).disableCull
            && GL11.glIsEnabled(GL11.GL_CULL_FACE);
        // Angelica expands wide lines to triangles; front-face culling would discard the wireframe.
        if (restoreCull) GLStateManager.disableCull();
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glPushMatrix();
        try {
            GLStateManager.glLoadIdentity();
            if (screenDistance) GLStateManager.glOrtho(0, 16, 0, 16, 1850.0F, 2150.0F);
            else GLStateManager.glOrtho(0, 16, 0, 16, -150.0F, 150.0F);
            GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
            if (screenDistance) GLStateManager.glTranslated(0, 0, -2000);
            RenderHelper.enableGUIStandardItemLighting();
            GLStateManager.enableRescaleNormal();
            GLStateManager.enableColorMaterial();
            GLStateManager.enableLighting();
            GLStateManager.glTranslated(8 * (1 - scale), 8 * (1 - scale), 0);
            GLStateManager.glScaled(scale, scale, scale);
            itemRenderer.renderItemAndEffectIntoGUI(minecraft.fontRenderer, minecraft.renderEngine, itemStack, 0, 0);
        } finally {
            if (restoreCull) GLStateManager.enableCull();
            GLStateManager.disableLighting();
            RenderHelper.disableStandardItemLighting();
            GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            fbo.end();
        }
        if (itemStack.getItem() instanceof ItemBlock) {
            Block block = ((ItemBlock) itemStack.getItem()).field_150939_a;
            if (CommonProxy.isGTLoaded && (block instanceof GTBlockOre || block instanceof BWMetaGeneratedOres
                || block instanceof BlockBaseOre)) {
                return makeNonTransparentOpaque(fbo.saveToImage());
            }
        }
        BufferedImage image = fbo.saveToImage();
        if (current != null && ExportSession.imageDiagnostics()) {
            String key = Utils.getItemStackShortKey(itemStack);
            if (image.getWidth() == SINGLE_FRAME_SIZE || !current.captureReport.containsKey(key)) {
                Map<String, Object> diagnostic = new LinkedHashMap<>();
                int visible = 0;
                for (int y = 0; y < image.getHeight(); y++)
                    for (int x = 0; x < image.getWidth(); x++) if ((image.getRGB(x, y) >>> 24) != 0) visible++;
                diagnostic.put("nonTransparentPixels", visible);
                diagnostic.put("renderItemZLevel", itemRenderer.zLevel);
                if (beforeFbo != null) {
                    diagnostic.put("beforeFbo", beforeFbo);
                    diagnostic.put("insideFbo", insideFbo);
                }
                diagnostic.put("width", image.getWidth());
                diagnostic.put("height", image.getHeight());
                net.minecraftforge.client.IItemRenderer renderer = net.minecraftforge.client.MinecraftForgeClient
                    .getItemRenderer(itemStack, net.minecraftforge.client.IItemRenderer.ItemRenderType.INVENTORY);
                diagnostic.put(
                    "renderer",
                    renderer == null ? null
                        : renderer.getClass()
                            .getName());
                diagnostic.put("nbt", Utils.getItemNBT(itemStack));
                current.captureReport.put(key, diagnostic);
            }
        }
        return image;
    }

    public static void renderGeneralItem(ItemStack itemStack, FBOHelper fbo, RenderItem itemRenderer) {
        itemStack = getBaseIconStack(itemStack);
        renderGeneralItem(itemStack, fbo, itemRenderer, getIconFileName(itemStack), getIconFileName(itemStack, true));
    }

    private static void renderGeneralItem(ItemStack itemStack, FBOHelper fbo, RenderItem itemRenderer,
        String staticName, String dynamicName) {
        IconHalo.take();
        drawGeneralItem(itemStack, fbo, itemRenderer, staticName, dynamicName);
        String halo = IconHalo.take();
        if (halo != null) halos.put(staticName, halo);
    }

    private static void drawGeneralItem(ItemStack itemStack, FBOHelper fbo, RenderItem itemRenderer, String staticName,
        String dynamicName) {
        // Only renders of 3D blocks may be stored lossy; flat item sprites stay pixel exact.
        if (is3dBlockItem(itemStack)) IconImageFormat.allowLossy(staticName);
        ItemIconRenderProfile.Sampling sampling = ItemIconRenderProfile.forStack(itemStack).sampling;
        if (sampling != null) {
            // Never resumed from earlier output: the frames follow the clock, not the textures.
            renderClockSampled(itemStack, itemRenderer, sampling, staticName, dynamicName);
            return;
        }
        GtTickAnimation tickAnimation = GtTickAnimation.forStack(itemStack);
        if (tickAnimation == null && hasCondensateOverlay(itemStack, itemRenderer))
            tickAnimation = GtTickAnimation.CONDENSATE;
        DynamicTexture dynamicTexture = new DynamicTexture(itemStack);
        // Prismarine animations have thousands of frames (6600 for the wall); export them as still images.
        boolean dynamic = tickAnimation != null
            || !staticName.startsWith("icon_Botania_prismarine") && dynamicTexture.isDynamic();
        if (resumeItem(itemStack, dynamic, staticName, dynamicName)) {
            // The halo is only known from drawing the item.
            if (IconHalo.isRemoving()) {
                FBOHelper probe = getFbo(GtTickAnimation.FRAME_SIZE);
                renderItem(itemStack, probe, itemRenderer, 1f, null);
                probe.restoreTexture();
            }
            return;
        }
        if (tickAnimation != null) {
            renderTickAnimation(itemStack, itemRenderer, tickAnimation, dynamicTexture, staticName, dynamicName);
            return;
        }
        if (dynamic) {
            renderDynamicItem(itemStack, itemRenderer, dynamicTexture, staticName, dynamicName);
            return;
        }
        IconRenderClock.takeStarfieldDrawn();
        BufferedImage image = renderItem(itemStack, fbo, itemRenderer, 1f, null);
        boolean starfield = IconRenderClock.takeStarfieldDrawn();
        if (starfield) image = renderAtScreenDistance(itemStack, fbo, itemRenderer);
        saveStill(staticName, image);
        fbo.restoreTexture();
        if (starfield) renderStarfieldAnimation(itemStack, itemRenderer, dynamicTexture, staticName, dynamicName);
    }

    /** A fluid display whose picture changes with the overlay frame, whatever drew it. */
    private static boolean hasCondensateOverlay(ItemStack itemStack, RenderItem itemRenderer) {
        if (!GtCondensateOverlay.isFluidDisplay(itemStack)) return false;
        FBOHelper probe = getFbo(GtTickAnimation.FRAME_SIZE);
        BufferedImage first = renderItem(itemStack, probe, itemRenderer, 1f, null);
        // GregTech's overlay texture repeats every half of its frames, so half way looks like the start.
        condensateFrame = GtCondensateOverlay.FRAMES / 4;
        try {
            BufferedImage later = renderItem(itemStack, probe, itemRenderer, 1f, null);
            return !deduplicateByPixel(new BufferedImage[] { first, later });
        } finally {
            condensateFrame = 0;
            probe.restoreTexture();
        }
    }

    private static BufferedImage renderAtScreenDistance(ItemStack itemStack, FBOHelper fbo, RenderItem itemRenderer) {
        screenDistance = true;
        try {
            return renderItem(itemStack, fbo, itemRenderer, 1f, null);
        } finally {
            screenDistance = false;
        }
    }

    private static void saveStill(String staticName, BufferedImage still) {
        pixelStills.put(staticName, IconImageFormat.isPixelArt(still));
        FBOHelper.saveToFile(iconOutputFile("icons/" + staticName), still);
    }

    private static void renderStarfieldAnimation(ItemStack itemStack, RenderItem itemRenderer, DynamicTexture texture,
        String staticName, String dynamicName) {
        int frameCount = StarfieldLoop.frameCount(texture.isDynamic() ? texture.lcm : 1);
        FBOHelper frameFbo = getFbo(STARFIELD_FRAME_SIZE);
        BufferedImage[] rendered = new BufferedImage[frameCount + StarfieldLoop.FADE_FRAMES];
        screenDistance = true;
        try {
            for (int i = 0; i < rendered.length; i++) {
                if (texture.isDynamic()) texture.uploadFrame(i % texture.lcm);
                IconRenderClock.setClockOffset(i * (long) StarfieldLoop.TICK_MILLIS);
                IconRenderClock.setEffectFrame(i % frameCount);
                rendered[i] = renderItem(itemStack, frameFbo, itemRenderer, 1f, null);
            }
        } finally {
            screenDistance = false;
            IconRenderClock.setClockOffset(0);
            IconRenderClock.setEffectFrame(0);
            if (texture.isDynamic()) texture.restore();
            frameFbo.restoreTexture();
        }
        BufferedImage[] frames = StarfieldLoop.close(rendered, StarfieldLoop.FADE_FRAMES);
        // The shader can be turned off in GT's or Avaritia's client settings; then there is nothing to animate.
        if (!deduplicateByPixel(rendered)) {
            saveStrip(itemStack, frames, dynamicName);
            pixelAnimations.put(staticName, false);
        }
        frameFbo.restoreTexture();
    }

    private static boolean resumeItem(ItemStack stack, boolean dynamic, String name, String animation) {
        if (resume == null) return false;
        JsonObject images = resume.getAsJsonObject("images");
        // An icon that only turned out to be animated once drawn (the star field) is rendered again.
        if (!images.has(name) || images.has(animation) != dynamic) return false;
        com.google.gson.JsonArray dimensions = images.getAsJsonArray(name);
        if (dimensions.get(0)
            .getAsInt() != SINGLE_FRAME_SIZE
            || dimensions.get(1)
                .getAsInt() != SINGLE_FRAME_SIZE)
            return false;
        int size = 0;
        int frames = 0;
        if (dynamic) {
            dimensions = images.getAsJsonArray(animation);
            size = dimensions.get(1)
                .getAsInt();
            int width = dimensions.get(0)
                .getAsInt();
            if (size <= 0 || width < size || width % size != 0) return false;
            frames = width / size;
        }
        // Link only fully validated files; missing pairs are rendered into new files.
        linkResumedImage(name);
        if (dynamic) {
            linkResumedImage(animation);
            frameCountMap.put(stack, frames);
            dynamicSizeMap.put(stack, size);
        }
        resumedIcons++;
        return true;
    }

    private static void linkResumedImage(String name) {
        java.nio.file.Path source = new File(
            resume.get("directory")
                .getAsString(),
            name).toPath();
        java.nio.file.Path target = iconOutputFile("icons/" + name).toPath();
        try {
            Files.createDirectories(target.getParent());
            try {
                Files.createLink(target, source);
            } catch (IOException unsupportedLink) {
                Files.copy(source, target);
            }
        } catch (IOException error) {
            throw new IllegalStateException("Could not reuse PNG: " + name, error);
        }
    }

    public static void renderDynamicItem(ItemStack itemStack, RenderItem itemRenderer, DynamicTexture dynamicTexture) {
        renderDynamicItem(
            itemStack,
            itemRenderer,
            dynamicTexture,
            getIconFileName(itemStack),
            getIconFileName(itemStack, true));
    }

    private static void renderDynamicItem(ItemStack itemStack, RenderItem itemRenderer, DynamicTexture dynamicTexture,
        String staticName, String dynamicName) {
        int frameCount = dynamicTexture.lcm;
        // The still icon is the first frame at full size, not an enlarged animation frame.
        BufferedImage still;
        int frameSize = SINGLE_FRAME_SIZE;
        boolean starfield;
        try {
            dynamicTexture.uploadFrame(0);
            IconRenderClock.takeStarfieldDrawn();
            still = renderItem(itemStack, getFbo(SINGLE_FRAME_SIZE), itemRenderer, 1f, null);
            starfield = IconRenderClock.takeStarfieldDrawn();
            if (starfield) still = renderAtScreenDistance(itemStack, getFbo(SINGLE_FRAME_SIZE), itemRenderer);
            if (!starfield && !is3dBlockItem(itemStack)) frameSize = chooseFrameSize(
                still,
                getDynamicTextureSize(dynamicTexture),
                frameCount,
                size -> renderItem(itemStack, getFbo(size), itemRenderer, 1f, null));
        } finally {
            dynamicTexture.restore();
            getFbo(SINGLE_FRAME_SIZE).restoreTexture();
        }
        if (starfield) {
            // The star field drifts while the texture animates: both advance in one loop.
            saveStill(staticName, still);
            renderStarfieldAnimation(itemStack, itemRenderer, dynamicTexture, staticName, dynamicName);
            return;
        }
        if (frameSize != SINGLE_FRAME_SIZE && frameSize != getDynamicTextureSize(dynamicTexture))
            detailAnimations.put(staticName, getDynamicTextureSize(dynamicTexture) + "->" + frameSize);
        long stripBytes = (long) frameCount * frameSize * frameSize * 4L;
        // The strip spans the least common multiple of all animation lengths and can exceed the heap.
        // Frames and the strip are both held, so peak use is about twice this limit.
        if (stripBytes > Long.getLong("gtnhdumper.maxAnimationMB", 256L) << 20) {
            GTNHDumper.LOG.warn(
                "Animated icon {} needs {} frames of {}px ({} MB); exporting a still image",
                staticName,
                frameCount,
                frameSize,
                stripBytes >> 20);
            synchronized (OVERSIZED_ANIMATIONS) {
                OVERSIZED_ANIMATIONS.put(staticName, frameCount + "x" + frameSize + "px=" + (stripBytes >> 20) + "MB");
            }
            saveStill(staticName, still);
            return;
        }
        FBOHelper dynamicFbo = getFbo(frameSize);
        BufferedImage[] images = new BufferedImage[frameCount];
        try {
            for (int i = 0; i < frameCount; i++) {
                dynamicTexture.uploadFrame(i);
                IconRenderClock.setEffectFrame(i);
                images[i] = renderItem(itemStack, dynamicFbo, itemRenderer, 1f, null);
            }
        } finally {
            IconRenderClock.setEffectFrame(0);
            dynamicTexture.restore();
            dynamicFbo.restoreTexture();
        }
        saveStill(staticName, still);
        saveStrip(itemStack, images, dynamicName);
        // Frames smaller than the detail size were checked to reproduce the full render: they are the sprite itself.
        pixelAnimations.put(staticName, frameSize < DETAIL_FRAME_SIZE && !is3dBlockItem(itemStack));
        dynamicFbo.restoreTexture();
    }

    /** Renderers driven by wall-clock time: the frozen clock advances by one interval per frame. */
    private static void renderClockSampled(ItemStack itemStack, RenderItem itemRenderer,
        ItemIconRenderProfile.Sampling sampling, String staticName, String dynamicName) {
        FBOHelper frameFbo = getFbo(sampling.frameSize);
        BufferedImage[] frames = new BufferedImage[sampling.frameCount];
        BufferedImage still;
        try {
            for (int i = 0; i < frames.length; i++) {
                IconRenderClock.setClockOffset(i * (long) sampling.intervalMillis);
                IconRenderClock.setEffectFrame(i);
                frames[i] = renderItem(itemStack, frameFbo, itemRenderer, 1f, null);
            }
            IconRenderClock.setClockOffset(0);
            IconRenderClock.setEffectFrame(0);
            still = renderItem(itemStack, getFbo(SINGLE_FRAME_SIZE), itemRenderer, 1f, null);
        } finally {
            IconRenderClock.setClockOffset(0);
            IconRenderClock.setEffectFrame(0);
            frameFbo.restoreTexture();
        }
        saveStill(staticName, still);
        saveStrip(itemStack, frames, dynamicName);
        pixelAnimations.put(staticName, false);
        frameFbo.restoreTexture();
    }

    private static void saveStrip(ItemStack itemStack, BufferedImage[] frames, String dynamicName) {
        BufferedImage strip = concatenateImages(frames, false);
        frameCountMap.put(itemStack, strip.getWidth() / strip.getHeight());
        dynamicSizeMap.put(itemStack, strip.getHeight());
        FBOHelper.saveToFile(iconOutputFile("icons/" + dynamicName), strip);
    }

    private static void renderTickAnimation(ItemStack itemStack, RenderItem itemRenderer, GtTickAnimation animation,
        DynamicTexture texture, String staticName, String dynamicName) {
        // Some of these also have an animated texture of their own; both advance together.
        GtTickAnimation.Loop loop = animation.loop(texture.isDynamic() ? texture.lcm : 1);
        // The overlay is flat pixel art over the fluid texture: frames at its native size lose nothing.
        int nativeSide = animation == GtTickAnimation.CONDENSATE
            ? IconImageFormat.nativeSide(renderItem(itemStack, getFbo(SINGLE_FRAME_SIZE), itemRenderer, 1f, null))
            : 0;
        getFbo(SINGLE_FRAME_SIZE).restoreTexture();
        int frameSize = nativeSide > 0 ? nativeSide : GtTickAnimation.FRAME_SIZE;
        if ((long) loop.frameCount * frameSize > MAX_STRIP_WIDTH)
            throw new IllegalStateException("Animation of " + staticName + " needs " + loop.frameCount + " frames");
        FBOHelper frameFbo = getFbo(frameSize);
        BufferedImage[] frames = new BufferedImage[loop.frameCount];
        BufferedImage still;
        try {
            for (int i = 0; i < frames.length; i++) {
                if (texture.isDynamic()) texture.uploadFrame(i);
                animation.show(loop.tickAt(i));
                IconRenderClock.setEffectFrame(i);
                if (animation == GtTickAnimation.CONDENSATE) condensateFrame = (int) loop.tickAt(i);
                frames[i] = renderItem(itemStack, frameFbo, itemRenderer, 1f, null);
            }
            if (texture.isDynamic()) texture.uploadFrame(0);
            condensateFrame = 0;
            animation.show(0);
            IconRenderClock.setEffectFrame(0);
            still = renderItem(itemStack, getFbo(SINGLE_FRAME_SIZE), itemRenderer, 1f, null);
        } finally {
            condensateFrame = 0;
            IconRenderClock.setEffectFrame(0);
            if (texture.isDynamic()) texture.restore();
            animation.reset();
            frameFbo.restoreTexture();
        }
        saveStill(staticName, still);
        // Where the fluid texture allows it, the overlay's own repeat leaves the loop twice over.
        if (animation == GtTickAnimation.CONDENSATE) frames = withoutRepeats(frames);
        // GT's client settings can turn these effects off; then there is nothing to animate.
        if (!deduplicateByPixel(frames)) {
            saveStrip(itemStack, frames, dynamicName);
            // Rotating and colour-cycling sprites are rasterised anew each frame; the condensate stays pixel art.
            pixelAnimations.put(staticName, nativeSide > 0);
        }
        frameFbo.restoreTexture();
    }

    /** The shortest leading part of a loop that, played over and over, shows the same frames. */
    private static BufferedImage[] withoutRepeats(BufferedImage[] frames) {
        while (frames.length % 2 == 0) {
            int half = frames.length / 2;
            for (int i = 0; i < half; i++)
                if (!deduplicateByPixel(new BufferedImage[] { frames[i], frames[i + half] })) return frames;
            frames = java.util.Arrays.copyOf(frames, half);
        }
        return frames;
    }

    /**
     * Picks the animation frame size. Flat sprites lose nothing at their texture resolution: enlarged with nearest
     * neighbour they equal the full-size render, so that small size is kept. Custom renderers that draw cubes, rotated
     * or layered shapes through the same textures do not, and would turn into jagged thumbnails; they get more pixels.
     */
    static int chooseFrameSize(BufferedImage fullSize, int textureSize, int frameCount,
        java.util.function.IntFunction<BufferedImage> render) {
        for (int size : new int[] { textureSize, 32, 64 }) {
            if (size < textureSize || size >= DETAIL_FRAME_SIZE) continue;
            if (reproduces(fullSize, render.apply(size))) return size;
        }
        int size = Math.max(textureSize, DETAIL_FRAME_SIZE);
        // Wider strips than this are shown as still images by the wiki.
        while (size / 2 >= Math.max(textureSize, 32) && (long) size * frameCount > MAX_STRIP_WIDTH) size /= 2;
        return size;
    }

    private static boolean reproduces(BufferedImage fullSize, BufferedImage small) {
        int side = fullSize.getWidth();
        int[] expected = fullSize.getRGB(0, 0, side, side, null, 0, side);
        int[] actual = resizeImage(small, side).getRGB(0, 0, side, side, null, 0, side);
        // A few edge pixels may round differently between viewport sizes.
        int allowed = expected.length / 1000;
        for (int i = 0; i < expected.length; i++) {
            if (expected[i] == actual[i] || (expected[i] >>> 24) == 0 && (actual[i] >>> 24) == 0) continue;
            for (int shift = 0; shift < 32; shift += 8)
                if (Math.abs((expected[i] >>> shift & 0xff) - (actual[i] >>> shift & 0xff)) > 8) {
                    if (--allowed < 0) return false;
                    break;
                }
        }
        return true;
    }

    private static boolean is3dBlockItem(ItemStack itemStack) {
        return itemStack.getItem() instanceof ItemBlock
            && RenderBlocks.renderItemIn3d(((ItemBlock) itemStack.getItem()).field_150939_a.getRenderType());
    }

    private static int getDynamicTextureSize(DynamicTexture dynamicTexture) {
        return dynamicTexture.textures.stream()
            .mapToInt(texture -> Math.max(texture.getIconWidth(), texture.getIconHeight()))
            .max()
            .orElse(SINGLE_FRAME_SIZE);
    }

    private static BufferedImage resizeImage(BufferedImage image, int size) {
        if (image.getWidth() == size && image.getHeight() == size) {
            return image;
        }
        BufferedImage resized = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = resized.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2d.setComposite(AlphaComposite.Src);
        g2d.drawImage(image, 0, 0, size, size, null);
        g2d.dispose();
        return resized;
    }

    /**
     * 横向拼接BufferedImage数组（自动处理高度不一致、透明度）
     *
     * @param images 要拼接的图像数组
     * @return 拼接后的大图
     */
    public static BufferedImage concatenateImages(BufferedImage[] images) {
        return concatenateImages(images, true);
    }

    static BufferedImage concatenateImages(BufferedImage[] images, boolean deduplicate) {
        if (images == null || images.length == 0) {
            throw new IllegalArgumentException("Image array cannot be empty");
        }

        if (deduplicate && deduplicateByPixel(images)) {
            return images[0];
        }

        // 1. 计算目标图像尺寸
        int totalWidth = Arrays.stream(images)
            .mapToInt(BufferedImage::getWidth)
            .sum();
        int maxHeight = Arrays.stream(images)
            .mapToInt(BufferedImage::getHeight)
            .max()
            .orElse(0);

        // 2. 创建带透明通道的目标图像 (ARGB)
        BufferedImage result = new BufferedImage(totalWidth, maxHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = result.createGraphics();

        // 关键渲染设置（消除锯齿，保持透明度）
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setComposite(AlphaComposite.Src); // 保持源图像透明度

        // 3. 逐个绘制图像
        int currentX = 0;
        for (BufferedImage img : images) {
            int y = (maxHeight - img.getHeight()) / 2; // 垂直居中对齐
            g2d.drawImage(img, currentX, y, null);
            currentX += img.getWidth();
        }

        g2d.dispose();
        return result;
    }

    public static BufferedImage makeNonTransparentOpaque(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = img.getRGB(x, y);
                int alpha = (argb >> 24) & 0xFF;
                if (alpha != 0) { // 不是完全透明
                    argb = (0xFF << 24) | (argb & 0x00FFFFFF);
                    img.setRGB(x, y, argb);
                }
            }
        }
        return img;
    }

    /**
     * 如果数组中所有图像像素完全相同，返回仅包含第一个元素的数组；否则返回原数组。
     *
     * @param images BufferedImage 数组
     * @return 是否有重复
     */
    public static boolean deduplicateByPixel(BufferedImage[] images) {
        if (images == null || images.length <= 1) {
            return true;
        }

        BufferedImage first = images[0];
        // 如果第一张为 null，则直接返回原数组（或根据需求处理）
        if (first == null) {
            return false;
        }

        int width = first.getWidth();
        int height = first.getHeight();

        // 逐张比较
        for (int i = 1; i < images.length; i++) {
            BufferedImage curr = images[i];
            if (curr == null) {
                return false; // 存在 null，视为不同
            }
            // 尺寸不一致直接判定不同
            if (curr.getWidth() != width || curr.getHeight() != height) {
                return false;
            }
            // 快速比较：获取像素数组整数表示
            int[] firstPixels = first.getRGB(0, 0, width, height, null, 0, width);
            int[] currPixels = curr.getRGB(0, 0, width, height, null, 0, width);
            if (!Arrays.equals(firstPixels, currPixels)) {
                return false;
            }
        }

        // 全部相同，返回只含第一张的数组
        return true;
    }

    public static Integer getItemDynamicSize(ItemStack stack) {
        return dynamicSizeMap.containsKey(stack) ? dynamicSizeMap.getInt(stack) : null;
    }

    public static Integer getItemFrameCount(ItemStack stack) {
        return frameCountMap.containsKey(stack) ? frameCountMap.getInt(stack) : null;
    }
}
