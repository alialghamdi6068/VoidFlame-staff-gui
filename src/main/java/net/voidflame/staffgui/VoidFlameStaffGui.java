package net.voidflame.staffgui;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public final class VoidFlameStaffGui extends JavaPlugin implements Listener, CommandExecutor {
    private final Map<UUID, Player> targets = new HashMap<>();
    private final Map<UUID, Long> confirmations = new HashMap<>();

    private enum Action {
        HEAL, SURVIVAL, CREATIVE, ADVENTURE, SPECTATOR, PROTECTION, FLY,
        WALK_SPEED, FLY_SPEED, POTIONS, REPAIR, ARMOR, CLEAR_INV, CLEAR_ARROWS,
        XP, INVENTORY, WORKBENCH, PLAYER_MANAGER, KICK_ALL, TIME_WEATHER,
        SERVER_MANAGER, WORLD_MANAGER, CLEAR_CHAT, FLAGS
    }

    @Override public void onEnable() {
        saveDefaultConfig();
        Objects.requireNonNull(getCommand("staff")).setExecutor(this);
        Objects.requireNonNull(getCommand("staffgui")).setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("VoidFlame Staff GUI enabled.");
    }

    @Override public void onDisable() {
        targets.clear();
        confirmations.clear();
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
        if (!p.hasPermission("voidflame.staff")) { msg(p, "no-permission"); return true; }
        openMain(p);
        return true;
    }

    private void openMain(Player p) {
        int rows = Math.max(1, Math.min(6, getConfig().getInt("gui.rows", 6)));
        Inventory inv = Bukkit.createInventory(new Holder("main"), rows * 9, color(getConfig().getString("gui.title")));
        fill(inv);
        item(inv, 10, Material.GOLDEN_APPLE, "&6Heal", "Restore health, fire and food.");
        item(inv, 11, Material.COMMAND_BLOCK, "&bGamemode", "Choose Survival, Creative, Adventure or Spectator.");
        item(inv, 12, Material.NETHERITE_CHESTPLATE, "&5Protection Mode", "Toggle invulnerability for the target.");
        item(inv, 13, Material.ELYTRA, "&3Fly", "Toggle flight for the target.");
        item(inv, 14, Material.SUGAR, "&fWalk Speed", "Cycle target walk speed.");
        item(inv, 15, Material.FEATHER, "&fFly Speed", "Cycle target fly speed.");
        item(inv, 16, Material.POTION, "&dPotion Effects", "Add or clear potion effects.");
        item(inv, 19, Material.ANVIL, "&7Item Repair", "Repair inventory and worn armor.");
        item(inv, 20, Material.DIAMOND_CHESTPLATE, "&bArmor Creator", "Give a complete enchanted armor set.");
        item(inv, 21, Material.LAVA_BUCKET, "&cClear Inventory", "Clear the target inventory.");
        item(inv, 22, Material.ARROW, "&eClear Arrows", "Remove embedded arrows.");
        item(inv, 23, Material.EXPERIENCE_BOTTLE, "&aExperience Manager", "Cycle XP levels.");
        item(inv, 24, Material.CHEST, "&bMy Inventory", "Inspect your inventory.");
        item(inv, 25, Material.CRAFTING_TABLE, "&6Workbench", "Open a crafting table.");
        item(inv, 28, Material.PLAYER_HEAD, "&ePlayer Manager", "Select and manage an online player.");
        item(inv, 29, Material.BARRIER, "&cKick All Players", "Kick all non-staff players.");
        item(inv, 30, Material.CLOCK, "&6Time & Weather", "Cycle time and weather.");
        item(inv, 31, Material.REDSTONE_BLOCK, "&cServer Manager", "Stop/reload/whitelist controls.");
        item(inv, 32, Material.GRASS_BLOCK, "&2World Manager", "World teleport and world creation.");
        item(inv, 33, Material.PAPER, "&fClear Chat", "Clear the public chat.");
        item(inv, 34, Material.REPEATER, "&9Server Flags", "Toggle PvP, block placement and mob spawning.");
        item(inv, 49, Material.NETHER_STAR, "&5Target: " + targetName(p), "Right-click a player to set target.");
        p.openInventory(inv);
    }

    private String targetName(Player p) { Player t = targets.get(p.getUniqueId()); return t == null ? "None" : t.getName(); }

    private void fill(Inventory inv) {
        Material m = Material.matchMaterial(getConfig().getString("gui.filler", "BLACK_STAINED_GLASS_PANE"));
        if (m == null) m = Material.BLACK_STAINED_GLASS_PANE;
        ItemStack filler = named(m, " ");
        for (int i=0;i<inv.getSize();i++) inv.setItem(i, filler);
    }

    private void item(Inventory inv, int slot, Material mat, String name, String lore) {
        if (slot >= inv.getSize()) return;
        inv.setItem(slot, named(mat, name, lore));
    }

    private ItemStack named(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            meta.setLore(Arrays.stream(lore).map(this::color).toList());
            it.setItemMeta(meta);
        }
        return it;
    }

    @EventHandler public void click(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!e.getClickedInventory().equals(e.getView().getTopInventory())) return;
        if (h.type.equals("main")) handleMain(p, e.getRawSlot());
        else if (h.type.equals("confirm")) handleConfirm(p, e.getRawSlot());
        else if (h.type.equals("players")) handlePlayers(p, e.getRawSlot());
        else if (h.type.equals("gamemode")) handleGamemode(p, e.getRawSlot());
        else if (h.type.equals("server")) handleServer(p, e.getRawSlot());
        else if (h.type.equals("world")) handleWorld(p, e.getRawSlot());
        else if (h.type.equals("flags")) handleFlags(p, e.getRawSlot());
        else if (h.type.equals("potions")) handlePotions(p, e.getRawSlot());
    }

    @EventHandler public void drag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }

    @EventHandler public void quit(PlayerQuitEvent e) { targets.values().removeIf(p -> p.getUniqueId().equals(e.getPlayer().getUniqueId())); }

    private void handleMain(Player p, int s) {
        Player t = target(p); if (t == null && s != 49 && s != 28) { msg(p, "target-required"); return; }
        switch(s) {
            case 10 -> heal(t);
            case 11 -> openGamemode(p);
            case 12 -> { t.setInvulnerable(!t.isInvulnerable()); done(p, "Protection Mode"); }
            case 13 -> { t.setAllowFlight(!t.getAllowFlight()); t.setFlying(t.getAllowFlight()); done(p, "Fly"); }
            case 14 -> { t.setWalkSpeed(nextSpeed(t.getWalkSpeed())); done(p, "Walk Speed"); }
            case 15 -> { t.setFlySpeed(nextSpeed(t.getFlySpeed())); done(p, "Fly Speed"); }
            case 16 -> openPotions(p);
            case 19 -> { for (ItemStack i:t.getInventory().getContents()) if(i!=null)i.setDurability((short)0); for(ItemStack i:t.getInventory().getArmorContents()) if(i!=null)i.setDurability((short)0); done(p,"Repair"); }
            case 20 -> armor(t);
            case 21 -> confirm(p, Action.CLEAR_INV);
            case 22 -> { t.getInventory().setItemInOffHand(t.getInventory().getItemInOffHand()); done(p,"Clear Arrows"); }
            case 23 -> { t.giveExpLevels(5); done(p,"Experience"); }
            case 24 -> { p.openInventory(t.getInventory()); }
            case 25 -> p.openWorkbench(null, true);
            case 28 -> openPlayers(p);
            case 29 -> confirm(p, Action.KICK_ALL);
            case 30 -> timeWeather(p);
            case 31 -> openServer(p);
            case 32 -> openWorld(p);
            case 33 -> clearChat(p);
            case 34 -> openFlags(p);
            case 49 -> openMain(p);
        }
    }

    private void heal(Player t) { t.setHealth(t.getMaxHealth()); t.setFireTicks(0); t.setFoodLevel(getConfig().getInt("settings.heal-food-level",20)); t.setSaturation(20); t.setExhaustion(0); done(t,"Heal"); }
    private float nextSpeed(float current) { double max=getConfig().getDouble("settings.max-speed",1.0); float n=current>=max-0.01?0.2f:Math.min((float)max,current+0.2f); return n; }

    private void openGamemode(Player p) {
        Inventory i=Bukkit.createInventory(new Holder("gamemode"),27,color("&8Gamemode"));
        fill(i); item(i,10,Material.GRASS_BLOCK,"&aSurvival","Set target to Survival."); item(i,11,Material.COMMAND_BLOCK,"&bCreative","Set target to Creative."); item(i,12,Material.FEATHER,"&eAdventure","Set target to Adventure."); item(i,13,Material.ENDER_EYE,"&5Spectator","Set target to Spectator."); p.openInventory(i);
    }
    private void handleGamemode(Player p,int s){Player t=target(p);if(t==null)return;GameMode gm=s==10?GameMode.SURVIVAL:s==11?GameMode.CREATIVE:s==12?GameMode.ADVENTURE:s==13?GameMode.SPECTATOR:null;if(gm!=null){t.setGameMode(gm);done(p,"Gamemode");p.closeInventory();openMain(p);}}

    private void openPotions(Player p){Inventory i=Bukkit.createInventory(new Holder("potions"),27,color("&8Potion Effects"));fill(i);item(i,10,Material.SUGAR,"&aSpeed","Apply Speed II for 2 minutes.");item(i,11,Material.IRON_CHESTPLATE,"&cResistance","Apply Resistance II for 2 minutes.");item(i,12,Material.FEATHER,"&bJump Boost","Apply Jump Boost II for 2 minutes.");item(i,13,Material.MILK_BUCKET,"&fClear Effects","Clear all effects.");p.openInventory(i);}
    private void handlePotions(Player p,int s){Player t=target(p);if(t==null)return;PotionEffectType type=s==10?PotionEffectType.SPEED:s==11?PotionEffectType.RESISTANCE:s==12?PotionEffectType.JUMP_BOOST:null;if(type!=null)t.addPotionEffect(new PotionEffect(type,2400,1));else if(s==13)t.clearActivePotionEffects();done(p,"Potion Effects");openMain(p);}

    private void openPlayers(Player p){Inventory i=Bukkit.createInventory(new Holder("players"),54,color("&8Select Player"));fill(i);int slot=0;for(Player t:Bukkit.getOnlinePlayers()){if(slot>=45)break;i.setItem(slot,named(Material.PLAYER_HEAD,"&e"+t.getName(),"Click to set target"));slot++;}item(i,49,Material.ARROW,"&7Back","Return");p.openInventory(i);}
    private void handlePlayers(Player p,int s){if(s==49){openMain(p);return;}ItemStack it=p.getOpenInventory().getTopInventory().getItem(s);if(it==null||it.getType()!=Material.PLAYER_HEAD)return;String n=ChatColor.stripColor(Objects.requireNonNull(it.getItemMeta()).getDisplayName());Player t=Bukkit.getPlayerExact(n);if(t!=null)targets.put(p.getUniqueId(),t);openMain(p);}

    private void openServer(Player p){Inventory i=Bukkit.createInventory(new Holder("server"),27,color("&8Server Manager"));fill(i);item(i,10,Material.REDSTONE,"&cReload","Reload plugin configurations.");item(i,11,Material.BARRIER,"&cStop Server","Stop the server.");item(i,12,Material.WHITE_WOOL,"&fWhitelist","Toggle whitelist.");p.openInventory(i);}
    private void handleServer(Player p,int s){if(s==10){reloadConfig();msg(p,"reloaded");openMain(p);}else if(s==11){confirm(p,Action.KICK_ALL);Bukkit.getScheduler().runTask(this,()->Bukkit.shutdown());}else if(s==12){Bukkit.setWhitelist(!Bukkit.hasWhitelist());done(p,"Whitelist");openMain(p);}}

    private void openWorld(Player p){Inventory i=Bukkit.createInventory(new Holder("world"),54,color("&8World Manager"));fill(i);int s=0;for(World w:Bukkit.getWorlds()){if(s>=45)break;i.setItem(s,named(Material.GRASS_BLOCK,"&a"+w.getName(),"Click to teleport."));s++;}item(i,49,Material.ENDER_PEARL,"&bCreate World","Creates a simple world named StaffWorld.");p.openInventory(i);}
    private void handleWorld(Player p,int s){if(s==49){if(!p.hasPermission("voidflame.staff.world"))return;String n="StaffWorld";World w=Bukkit.getWorld(n);if(w==null)w=Bukkit.createWorld(new WorldCreator(n));if(w!=null)p.teleport(w.getSpawnLocation());return;}ItemStack it=p.getOpenInventory().getTopInventory().getItem(s);if(it==null)return;String n=ChatColor.stripColor(Objects.requireNonNull(it.getItemMeta()).getDisplayName());World w=Bukkit.getWorld(n);if(w!=null)p.teleport(w.getSpawnLocation());}

    private void openFlags(Player p){Inventory i=Bukkit.createInventory(new Holder("flags"),27,color("&8Server Flags"));fill(i);item(i,10,Material.IRON_SWORD,"&cPvP","Toggle combat flag.");item(i,11,Material.OAK_PLANKS,"&aBlock Placement","Toggle block placement flag.");item(i,12,Material.ZOMBIE_SPAWN_EGG,"&2Mob Spawning","Toggle mob spawning flag.");p.openInventory(i);}
    private void handleFlags(Player p,int s){String k=s==10?"pvp":s==11?"block-place":s==12?"mob-spawning":null;if(k!=null){getConfig().set("flags."+k,!getConfig().getBoolean("flags."+k,true));saveConfig();done(p,k);openFlags(p);}}

    private void timeWeather(Player p){World w=p.getWorld();if(w.hasStorm()){w.setStorm(false);w.setTime(1000);}else{w.setStorm(true);w.setThundering(false);w.setTime(13000);}done(p,"Time & Weather");openMain(p);}
    private void clearChat(Player p){for(Player x:Bukkit.getOnlinePlayers())for(int i=0;i<getConfig().getInt("settings.clear-chat-lines",100);i++)x.sendMessage(" ");done(p,"Clear Chat");openMain(p);}
    private void armor(Player t){ItemStack a=new ItemStack(Material.NETHERITE_HELMET),b=new ItemStack(Material.NETHERITE_CHESTPLATE),c=new ItemStack(Material.NETHERITE_LEGGINGS),d=new ItemStack(Material.NETHERITE_BOOTS);t.getInventory().setArmorContents(new ItemStack[]{d,c,b,a});done(t,"Armor Creator");}
    private void confirm(Player p,Action action){confirmations.put(p.getUniqueId(),System.currentTimeMillis()+15000);Inventory i=Bukkit.createInventory(new Holder("confirm"),27,color(getConfig().getString("gui.confirm-title")));fill(i);item(i,11,Material.LIME_CONCRETE,"&aConfirm","Proceed.");item(i,15,Material.RED_CONCRETE,"&cCancel","Cancel.");p.openInventory(i);}
    private void handleConfirm(Player p,int s){Long until=confirmations.get(p.getUniqueId());if(until==null||until<System.currentTimeMillis()){p.closeInventory();return;}if(s==15){confirmations.remove(p.getUniqueId());openMain(p);return;}if(s==11){confirmations.remove(p.getUniqueId());Player t=target(p);if(t!=null){t.getInventory().clear();done(p,"Clear Inventory");}openMain(p);}}
    private Player target(Player p){return targets.get(p.getUniqueId());}
    private void done(Player p,String action){if(p==null)return;msg(p,"action",Map.of("%action%",action));}
    private void msg(Player p,String key){msg(p,key,Map.of());}
    private void msg(Player p,String key,Map<String,String> vars){String s=getConfig().getString("messages."+key,key);for(var e:vars.entrySet())s=s.replace(e.getKey(),e.getValue());p.sendMessage(color(s));}
    private String color(String s){return ChatColor.translateAlternateColorCodes('&',s==null?"":s);}
    private static final class Holder implements InventoryHolder{final String type;Holder(String type){this.type=type;}@Override public Inventory getInventory(){return null;}}
}
