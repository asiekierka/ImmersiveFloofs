package uk.kihira.immersivefloofs;

import blusunrize.immersiveengineering.api.crafting.BlueprintCraftingRecipe;
import blusunrize.immersiveengineering.api.tool.BulletHandler;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBucketMilk;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.commons.io.IOUtils;
import uk.kihira.tails.common.PartsData;
import uk.kihira.tails.common.Tails;
import uk.kihira.tails.common.network.PlayerDataMessage;

import java.awt.*;
import java.io.IOException;
import java.util.UUID;

@Mod(modid = ImmersiveFloofs.MOD_ID, name = "Immersive Floofs", version = "1.0.0", dependencies = "required-after:immersiveengineering;required-after:tails")
public class ImmersiveFloofs {
    public static final String MOD_ID = "immersivefloofs";

    /** CONFIG **/
    private Configuration config;
    private boolean randomBullet = false;
    private boolean shooterBullet = true;
    //private boolean craftedBullet = true;
    private boolean milkResets = true;

    @GameRegistry.ObjectHolder(value = "immersiveengineering:bullet")
    public static final Item IE_ITEM_BULLET = Items.APPLE;

    @Mod.EventHandler
    public void onPreInit(FMLPreInitializationEvent e) {
        /* Load config */
        config = new Configuration(e.getSuggestedConfigurationFile());
        shooterBullet = config.getBoolean("Shooter Floof Bullet", Configuration.CATEGORY_GENERAL, true, "This bullet will apply the shooters parts data to the target");
        randomBullet = config.getBoolean("Random Floof Bullet", Configuration.CATEGORY_GENERAL, false, "This bullet will apply random parts from a list");
        milkResets = config.getBoolean("Milk Resets", Configuration.CATEGORY_GENERAL, true, "Whether drinking milk resets the 'effect'");

        randomBullet = true;

        /* Register bullets **/
        if (shooterBullet) BulletHandler.registerBullet("floof_shooter", new FloofBullet(new ResourceLocation[]{new ResourceLocation(ImmersiveFloofs.MOD_ID, "floof_shooter")}) {
            @Override
            public Entity getProjectile(EntityPlayer shooter, ItemStack cartridge, Entity projectile, boolean charged) {
                if (Tails.proxy.hasPartsData(shooter.getPersistentID())) {
                    projectile.getEntityData().setString("immersivefloofs", Tails.gson.toJson(Tails.proxy.getPartsData(shooter.getPersistentID())));
                }
                return projectile;
            }
        });
        if (randomBullet) {
            // Blargh code but only way to not get the getProjectile stuck inside a try/catch loop and not load on corrupted data
            JsonArray parts = null;
            try {
                parts = new JsonParser().parse(IOUtils.toString(ImmersiveFloofs.class.getResourceAsStream("/assets/immersivefloofs/random_tails.txt"))).getAsJsonArray();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            if (parts == null) throw new IllegalStateException("Failed to load random tail data for random bullet!");

            final JsonArray finalParts = parts;
            BulletHandler.registerBullet("floof_random", new FloofBullet(new ResourceLocation[]{new ResourceLocation(ImmersiveFloofs.MOD_ID, "floof_random0"), new ResourceLocation(ImmersiveFloofs.MOD_ID, "floof_random1")}) {
                @Override
                public Entity getProjectile(EntityPlayer shooter, ItemStack cartridge, Entity projectile, boolean charged) {
                    projectile.getEntityData().setString("immersivefloofs", finalParts.get(shooter.getRNG().nextInt(finalParts.size())).toString());
                    return projectile;
                }

                @Override
                public int getColour(ItemStack stack, int layer) {
                    if (layer == 1) {
                        return Color.getHSBColor(((Minecraft.getMinecraft().world.getTotalWorldTime() + Minecraft.getMinecraft().getRenderPartialTicks()) % 360f) / 360f, 1f, 1f).getRGB();
                    }
                    return 0xffffffff;
                }
            });
        }

        MinecraftForge.EVENT_BUS.register(this);
    }

    @Mod.EventHandler
    public void onInit(FMLInitializationEvent e) {
        if (IE_ITEM_BULLET == null || IE_ITEM_BULLET == Items.APPLE) throw new IllegalStateException("Unable to load IE bullet item!");
        /* Register recipes */
        ItemStack output; // Metadata must be 2 or above but doesn't seem to really matter
        if (shooterBullet) {
            output = new ItemStack(IE_ITEM_BULLET, 1, 2);
            output.setTagCompound(new NBTTagCompound() {{setString("bullet", "floof_shooter");}});
            BlueprintCraftingRecipe.addRecipe("specialBullet", output, IE_ITEM_BULLET, Items.GUNPOWDER, Blocks.WOOL);
        }
        if (randomBullet) {
            output = new ItemStack(IE_ITEM_BULLET, 1, 2);
            output.setTagCompound(new NBTTagCompound() {{setString("bullet", "floof_random");}});
            BlueprintCraftingRecipe.addRecipe("specialBullet", output, IE_ITEM_BULLET, Items.GUNPOWDER, Items.LEATHER);
        }
    }

    @SubscribeEvent
    public void onMilkDrink(LivingEntityUseItemEvent.Finish e) {
        if (milkResets && e.getItem().getItem() instanceof ItemBucketMilk && FloofBullet.oldPartCache.containsKey(e.getEntityLiving().getPersistentID())) {
            UUID uuid = e.getEntityLiving().getPersistentID();
            PartsData oldParts = FloofBullet.oldPartCache.get(uuid);
            boolean remove = oldParts == null;
            if (remove) {
                Tails.proxy.removePartsData(uuid);
            }
            else {
                Tails.proxy.addPartsData(uuid, oldParts);
            }
            if (FMLCommonHandler.instance().getSide() == Side.SERVER) {
                Tails.networkWrapper.sendToAll(new PlayerDataMessage(uuid, oldParts, remove));
            }
        }
    }
}
