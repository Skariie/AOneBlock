package world.bentobox.oneblock.listeners;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.PlayerBucketEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.eclipse.jdt.annotation.NonNull;

import world.bentobox.bentobox.api.events.island.IslandEvent.IslandCreatedEvent;
import world.bentobox.bentobox.api.events.island.IslandEvent.IslandDeleteEvent;
import world.bentobox.bentobox.api.events.island.IslandEvent.IslandResettedEvent;
import world.bentobox.bentobox.database.Database;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.oneblock.OneBlock;
import world.bentobox.oneblock.dataobjects.OneBlockIslands;

/**
 * @author tastybento
 *
 */
public class BlockListener implements Listener {

    private final OneBlock addon;
    private OneBlocksManager oneBlocksManager;
    private final Database<OneBlockIslands> handler;
    private final Map<String, OneBlockIslands> cache;
    private final Random random = new Random();
    /**
     * Water entities
     */
    private final static List<EntityType> WATER_ENTITIES = Arrays.asList(
            EntityType.GUARDIAN,
            EntityType.SQUID,
            EntityType.COD,
            EntityType.SALMON,
            EntityType.PUFFERFISH,
            EntityType.TROPICAL_FISH,
            EntityType.DROWNED,
            EntityType.DOLPHIN);

    private final static Map<EntityType, Sound> MOB_SOUNDS;
    public static final int MAX_LOOK_AHEAD = 5;
    static {
        Map<EntityType, Sound> m = new HashMap<>();
        m.put(EntityType.ZOMBIE, Sound.ENTITY_ZOMBIE_AMBIENT);
        m.put(EntityType.CREEPER, Sound.ENTITY_CREEPER_PRIMED);
        m.put(EntityType.SKELETON, Sound.ENTITY_SKELETON_AMBIENT);
        m.put(EntityType.DROWNED, Sound.ENTITY_DROWNED_AMBIENT);
        m.put(EntityType.BLAZE, Sound.ENTITY_BLAZE_AMBIENT);
        m.put(EntityType.CAVE_SPIDER, Sound.ENTITY_SPIDER_AMBIENT);
        m.put(EntityType.SPIDER, Sound.ENTITY_SPIDER_AMBIENT);
        m.put(EntityType.EVOKER, Sound.ENTITY_EVOKER_AMBIENT);
        m.put(EntityType.GHAST, Sound.ENTITY_GHAST_AMBIENT);
        m.put(EntityType.HUSK, Sound.ENTITY_HUSK_AMBIENT);
        m.put(EntityType.ILLUSIONER, Sound.ENTITY_ILLUSIONER_AMBIENT);
        m.put(EntityType.RAVAGER, Sound.ENTITY_RAVAGER_AMBIENT);
        m.put(EntityType.SHULKER, Sound.ENTITY_SHULKER_AMBIENT);
        m.put(EntityType.VEX, Sound.ENTITY_VEX_AMBIENT);
        m.put(EntityType.WITCH, Sound.ENTITY_WITCH_AMBIENT);
        m.put(EntityType.STRAY, Sound.ENTITY_STRAY_AMBIENT);
        MOB_SOUNDS = Collections.unmodifiableMap(m);
    }

    /**
     * @param addon - OneBlock
     * @throws InvalidConfigurationException - exception
     * @throws IOException - exception
     * @throws FileNotFoundException - exception
     */
    public BlockListener(OneBlock addon) throws FileNotFoundException, IOException, InvalidConfigurationException {
        this.addon = addon;
        handler = new Database<>(addon, OneBlockIslands.class);
        cache = new HashMap<>();
        oneBlocksManager = new OneBlocksManager(addon);
    }

    /**
     * Save the island cache
     */
    public void saveCache() {
        cache.values().forEach(handler::saveObject);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onNewIsland(IslandCreatedEvent e) {
        setUp(e.getIsland());
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDeletedIsland(IslandDeleteEvent e) {
        cache.remove(e.getIsland().getUniqueId());
        handler.deleteID(e.getIsland().getUniqueId());
    }

    private void setUp(Island island) {
        // Set the bedrock to the initial block
        island.getCenter().getBlock().setType(Material.GRASS_BLOCK);
        // Create a database entry
        OneBlockIslands is = new OneBlockIslands(island.getUniqueId());
        cache.put(island.getUniqueId(), is);
        handler.saveObject(is);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onNewIsland(IslandResettedEvent e) {
        setUp(e.getIsland());
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        if (!addon.inWorld(e.getBlock().getWorld())) {
            return;
        }
        Location l = e.getBlock().getLocation();
        addon.getIslands().getIslandAt(l).filter(i -> l.equals(i.getCenter())).ifPresent(i -> process(e, i, e.getPlayer()));
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(PlayerBucketEvent e) {
        if (!addon.inWorld(e.getBlock().getWorld())) {
            return;
        }
        Location l = e.getBlock().getLocation();
        addon.getIslands().getIslandAt(l).filter(i -> l.equals(i.getCenter())).ifPresent(i -> process(e, i, e.getPlayer()));
    }

    private void process(Cancellable e, Island i, @NonNull Player player) {
        if (e instanceof BlockBreakEvent) e.setCancelled(true);
        // Get island from cache or load it
        OneBlockIslands is = getIsland(i);
        // Get the phase for this island
        OneBlockPhase phase = oneBlocksManager.getPhase(is.getBlockNumber());
        // Announce the phase
        boolean newPhase = false;
        if (!is.getPhaseName().equalsIgnoreCase(phase.getPhaseName())) {
            cache.get(i.getUniqueId()).setPhaseName(phase.getPhaseName());
            player.sendTitle(phase.getPhaseName(), null, -1, -1, -1);
            newPhase = true;
        }
        // Get the block that is being broken
        Block block = i.getCenter().toVector().toLocation(player.getWorld()).getBlock();
        // Fill a 5 block queue
        if (is.getQueue().isEmpty() || newPhase) {
            // Add initial 5 blocks
            for (int j = 0; j < MAX_LOOK_AHEAD; j++) {
                is.add(phase.getNextBlock());
            }
        }
        // Play warning sound for upcoming mobs
        if (addon.getSettings().getMobWarning() > 0) {
            is.getNearestMob(random.nextInt(addon.getSettings().getMobWarning()) + 1).filter(MOB_SOUNDS::containsKey).map(MOB_SOUNDS::get).ifPresent(s -> block.getWorld().playSound(block.getLocation(), s, 1F, 1F));
        }
        // Get the next block
        OneBlockObject nextBlock = newPhase && phase.getFirstBlock() != null ? phase.getFirstBlock() : is.pop(phase.getNextBlock());
        // Set the biome for the block and one block above it
        if (newPhase) {
            block.getWorld().setBiome(block.getX(), block.getZ(), phase.getPhaseBiome());
            addon.logWarning("Setting biome at " + block.getX() + ", " + block.getZ() + " to " + phase.getPhaseBiome());
            //block.setBiome(phase.getPhaseBiome());
            //block.getRelative(BlockFace.UP).setBiome(phase.getPhaseBiome());
        }
        // Entity
        if (nextBlock.isEntity()) {
            // Entity spawns do not increment the block number or break the block
            spawnEntity(nextBlock, block);
            return;
        }
        // Break the block
        if (e instanceof BlockBreakEvent) {
            block.breakNaturally();
            player.giveExp(((BlockBreakEvent)e).getExpToDrop());
            // Damage tool
            damageTool(player);
        }

        @NonNull
        Material type = nextBlock.getMaterial();
        // Place new block with no physics
        block.setType(type, false);
        // Fill the chest
        if (type.equals(Material.CHEST) && nextBlock.getChest() != null) {
            fillChest(nextBlock, block);
        }
        // Increment the block number
        is.incrementBlockNumber();
    }

    private void spawnEntity(OneBlockObject nextBlock, Block block) {
        Location spawnLoc = block.getLocation().add(new Vector(0.5D, 1D, 0.5D));
        Entity entity = block.getWorld().spawnEntity(spawnLoc, nextBlock.getEntityType());
        // Make space for entity - this will blot out blocks
        if (entity != null) {
            makeSpace(entity);
            block.getWorld().playSound(block.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1F, 2F);
        } else {
            addon.logWarning("Could not spawn entity at " + spawnLoc);
        }
    }

    private void makeSpace(Entity e) {
        World world = e.getWorld();
        // Make space for entity based on the entity's size
        BoundingBox bb = e.getBoundingBox();
        for (double x = bb.getMinX(); x <= bb.getMaxX() + 1; x++) {
            for (double z = bb.getMinZ(); z <= bb.getMaxZ() + 1; z++) {
                double y = bb.getMinY();
                Block b = world.getBlockAt(new Location(world, x,y,z));
                for (; y <= Math.min(bb.getMaxY() + 1, world.getMaxHeight()); y++) {
                    b = world.getBlockAt(new Location(world, x,y,z));
                    if (!b.getType().equals(Material.AIR) && !b.isLiquid()) b.breakNaturally();
                    b.setType(WATER_ENTITIES.contains(e.getType()) ? Material.WATER : Material.AIR, false);
                }
                // Add air block on top for all water entities (required for dolphin, okay for others)
                if (WATER_ENTITIES.contains(e.getType())) {
                    b.getRelative(BlockFace.UP).setType(Material.AIR);
                }
            }
        }
    }


    private void fillChest(OneBlockObject nextBlock, Block block) {
        Chest chest = (Chest)block.getState();
        nextBlock.getChest().forEach(chest.getBlockInventory()::setItem);
        if (nextBlock.isRare()) {
            block.getWorld().spawnParticle(Particle.REDSTONE, block.getLocation().add(new Vector(0.5, 1.0, 0.5)), 50, 0.5, 0, 0.5, 1, new Particle.DustOptions(Color.fromBGR(50,255,255), 1));
        }
    }

    /**
     * Get the one block island data
     * @param i - island
     * @return one block island
     */
    public OneBlockIslands getIsland(Island i) {
        return cache.containsKey(i.getUniqueId()) ? cache.get(i.getUniqueId()) : loadIsland(i.getUniqueId());
    }

    private void damageTool(@NonNull Player player) {
        ItemStack inHand = player.getInventory().getItemInMainHand();
        ItemMeta itemMeta = inHand.getItemMeta();
        if (itemMeta instanceof Damageable && !itemMeta.isUnbreakable()) {
            Damageable meta = (Damageable) itemMeta;
            Integer damage = meta.getDamage();
            if (damage != null) {
                // Check for DURABILITY
                if (itemMeta.hasEnchant(Enchantment.DURABILITY)) {
                    int level = itemMeta.getEnchantLevel(Enchantment.DURABILITY);
                    if (random.nextInt(level + 1) == 0) {
                        meta.setDamage(damage + 1);
                    }
                } else {
                    meta.setDamage(damage + 1);
                }
                inHand.setItemMeta(itemMeta);
            }
        }

    }

    private OneBlockIslands loadIsland(String uniqueId) {
        if (handler.objectExists(uniqueId)) {
            OneBlockIslands island = handler.loadObject(uniqueId);
            if (island != null) {
                // Add to cache
                cache.put(island.getUniqueId(), island);
                return island;
            }
        }
        return cache.computeIfAbsent(uniqueId, OneBlockIslands::new);
    }

    /**
     * @return the oneBlocksManager
     */
    public OneBlocksManager getOneBlocksManager() {
        return oneBlocksManager;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent e) {
        if (!addon.inWorld(e.getBlock().getWorld())) {
            return;
        }
        Block block = e.getBlock();
        Location l = block.getLocation();
        addon.getIslands().getIslandAt(l).filter(i -> l.equals(i.getCenter())).ifPresent(i ->
        block.getWorld().spawnParticle(Particle.REDSTONE, l.add(new Vector(0.5, 1.0, 0.5)), 5, 0.1, 0, 0.1, 1, new Particle.DustOptions(Color.fromBGR(0,100,0), 1)));
    }
    /*
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onRespawn(PlayerRespawnEvent e) {

    } */
}