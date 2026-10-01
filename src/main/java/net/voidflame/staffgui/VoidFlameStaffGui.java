package net.voidflame.staffgui;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.entity.EntityType;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;
import org.bukkit.enchantments.Enchantment;

import java.util.*;

public final class VoidFlameStaffGui extends JavaPlugin implements Listener, CommandExecutor {
    private final Map<UUID, UUID> targets = new HashMap<>();
    private final Map<UUID, Long> confirmations = new HashMap<>();
    private final Map<UUID, Action> pending = new HashMap<>();
    private boolean lockTime, lockWeather;
    private long lockedTime = 6000L;
    private boolean storm;
    private final Map<String, Boolean> flags = new HashMap<>();

    enum Action { CLEAR_INV, KICK_ALL, STOP, REMOVE_WORLD }

    @Override public void onEnable() {
        saveDefaultConfig();
        loadState();
        getCommand("staff").setExecutor(this);
        getCommand("staffgui").setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this, this::enforceLocks, 1L, 20L);
        getLogger().info("VoidFlame Staff GUI enabled.");
    }

    @Override public void onDisable() {
        targets.clear(); confirmations.clear(); pending.clear();
        saveState();
    }

    private void loadState() {
        flags.put("pvp", getConfig().getBoolean("flags.pvp", true));
        flags.put("block-place", getConfig().getBoolean("flags.block-place", true));
        flags.put("mob-spawning", getConfig().getBoolean("flags.mob-spawning", true));
        lockTime=getConfig().getBoolean("time-weather.lock-time",false);
        lockWeather=getConfig().getBoolean("time-weather.lock-weather",false);
        lockedTime=getConfig().getLong("time-weather.time",6000L);
        storm=getConfig().getBoolean("time-weather.storm",false);
    }

    private void saveState() {
        flags.forEach((k,v)->getConfig().set("flags."+k,v));
        getConfig().set("time-weather.lock-time",lockTime);
        getConfig().set("time-weather.lock-weather",lockWeather);
        getConfig().set("time-weather.time",lockedTime);
        getConfig().set("time-weather.storm",storm);
        saveConfig();
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
        if (!p.hasPermission("voidflame.staff")) { msg(p,"no-permission"); return true; }
        openMain(p); return true;
    }

    private Inventory gui(String type,int rows,String title) {
        Inventory i=Bukkit.createInventory(new Holder(type),rows*9,color(title));
        Material filler=Material.matchMaterial(getConfig().getString("gui.filler","BLACK_STAINED_GLASS_PANE"));
        if(filler==null) filler=Material.BLACK_STAINED_GLASS_PANE;
        ItemStack pane=item(filler," ");
        for(int n=0;n<i.getSize();n++) i.setItem(n,pane);
        return i;
    }

    private ItemStack item(Material m,String name,String... lore) {
        ItemStack i=new ItemStack(m); ItemMeta meta=i.getItemMeta();
        if(meta!=null){meta.setDisplayName(color(name));meta.setLore(Arrays.stream(lore).map(this::color).toList());i.setItemMeta(meta);}
        return i;
    }
    private void put(Inventory i,int slot,Material m,String name,String... lore){if(slot<i.getSize())i.setItem(slot,item(m,name,lore));}

    private void openMain(Player p) {
        Inventory i=gui("main",6,"&8VoidFlame &5Staff Control");
        put(i,10,Material.GOLDEN_APPLE,"&6Heal Me","Restore health, remove fire, refill hunger.");
        put(i,11,Material.COMMAND_BLOCK,"&bGamemode","Survival / Creative / Adventure / Spectator.");
        put(i,12,Material.NETHERITE_CHESTPLATE,"&5Protection Mode","Toggle invulnerability.");
        put(i,13,Material.ELYTRA,"&3Fly","Toggle flight.");
        put(i,14,Material.SUGAR,"&fWalk Speed","Open speed controls.");
        put(i,15,Material.FEATHER,"&fFly Speed","Open speed controls.");
        put(i,16,Material.POTION,"&dPotion Effects","Add, modify or clear effects.");
        put(i,19,Material.ANVIL,"&7Item Repair","Repair inventory and worn armor.");
        put(i,20,Material.DIAMOND_CHESTPLATE,"&bArmor Creator","Build an enchanted armor set.");
        put(i,21,Material.LAVA_BUCKET,"&cClear Inventory","Remove all target items.");
        put(i,22,Material.ARROW,"&eClear Arrows","Remove arrows from body.");
        put(i,23,Material.EXPERIENCE_BOTTLE,"&aExperience Manager","Add, remove or reset XP.");
        put(i,24,Material.CHEST,"&bMy EQ","Open your personal inventory.");
        put(i,25,Material.CRAFTING_TABLE,"&6Workbench","Open a crafting table.");
        put(i,28,Material.PLAYER_HEAD,"&ePlayer Manager","Select a player and manage stats/inventory.");
        put(i,29,Material.BARRIER,"&cKick All Players","Kick all players according to staff policy.");
        put(i,30,Material.CLOCK,"&6Time / Weather","Set and lock server time/weather.");
        put(i,31,Material.REDSTONE_BLOCK,"&cServer Manager","Stop, reload and whitelist.");
        put(i,32,Material.GRASS_BLOCK,"&2World Manager","Create, remove and teleport worlds.");
        put(i,33,Material.PAPER,"&fClear Chat","Clear chat for everyone.");
        put(i,34,Material.REPEATER,"&9Fast Flags","PvP, block placement, mob spawning.");
        Player t=target(p);
        put(i,49,Material.NETHER_STAR,"&5Target: &f"+(t==null?"None":t.getName()),"Open Player Manager to select a target.");
        p.openInventory(i);
    }

    @EventHandler public void click(InventoryClickEvent e) {
        if(!(e.getWhoClicked() instanceof Player p))return;
        if(!(e.getView().getTopInventory().getHolder() instanceof Holder h))return;
        if(e.getClickedInventory()==e.getView().getTopInventory()) e.setCancelled(true); else return;
        int s=e.getRawSlot();
        switch(h.type){
            case "main" -> main(p,s);
            case "players" -> players(p,s);
            case "player" -> playerManager(p,s);
            case "gamemode" -> gamemode(p,s);
            case "speed" -> speed(p,s);
            case "potions" -> potions(p,s);
            case "xp" -> xp(p,s);
            case "armor" -> armorMenu(p,s);
            case "server" -> server(p,s);
            case "world" -> worlds(p,s,e.isRightClick());
            case "time" -> time(p,s);
            case "flags" -> flagMenu(p,s);
            case "confirm" -> confirm(p,s);
        }
    }

    @EventHandler public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof Holder)e.setCancelled(true);}
    @EventHandler public void quit(PlayerQuitEvent e){targets.entrySet().removeIf(x->x.getValue().equals(e.getPlayer().getUniqueId()));}

    private void main(Player p,int s){
        Player t=target(p);
        if(s==28){openPlayers(p);return;}
        if(s==24){p.openInventory(p.getInventory());return;}
        if(s==25){p.openWorkbench(null,true);return;}
        if(t==null){msg(p,"target-required");return;}
        switch(s){
            case 10 -> {heal(t);done(p,"Heal Me");}
            case 11 -> openGamemode(p);
            case 12 -> {t.setInvulnerable(!t.isInvulnerable());done(p,"Protection Mode");}
            case 13 -> {t.setAllowFlight(!t.getAllowFlight());t.setFlying(t.getAllowFlight());done(p,"Fly");}
            case 14,15 -> openSpeed(p);
            case 16 -> openPotions(p);
            case 19 -> {for(ItemStack x:t.getInventory().getContents())repair(x);for(ItemStack x:t.getInventory().getArmorContents())repair(x);repair(t.getInventory().getItemInOffHand());done(p,"Item Repair");}
            case 20 -> openArmor(p);
            case 21 -> confirm(p,Action.CLEAR_INV);
            case 22 -> {t.setArrowsInBody(0);done(p,"Clear Arrows from Body");}
            case 23 -> openXp(p);
            case 29 -> confirm(p,Action.KICK_ALL);
            case 30 -> openTime(p);
            case 31 -> openServer(p);
            case 32 -> openWorlds(p);
            case 33 -> clearChat(p);
            case 34 -> openFlags(p);
        }
    }

    private void heal(Player t){t.setHealth(t.getMaxHealth());t.setFireTicks(0);t.setFoodLevel(20);t.setSaturation(20);t.setExhaustion(0);}
    private void repair(ItemStack x){if(x==null||x.getType().isAir())return;ItemMeta m=x.getItemMeta();if(m instanceof Damageable d){d.setDamage(0);x.setItemMeta(d);}}
    private Player target(Player p){UUID id=targets.get(p.getUniqueId());return id==null?null:Bukkit.getPlayer(id);}

    private void openPlayers(Player p){if(!p.hasPermission("voidflame.staff.players")){msg(p,"no-permission");return;}
        Inventory i=gui("players",6,"&8Select Player");int slot=0;
        for(Player t:Bukkit.getOnlinePlayers()){if(slot>=45)break;put(i,slot,Material.PLAYER_HEAD,"&e"+t.getName(),"Click to select this player.");slot++;}
        put(i,49,Material.ARROW,"&7Back");p.openInventory(i);
    }
    private void players(Player p,int s){
        if(s==49){openMain(p);return;}ItemStack x=p.getOpenInventory().getTopInventory().getItem(s);
        if(x==null||x.getType()!=Material.PLAYER_HEAD||x.getItemMeta()==null)return;
        String n=ChatColor.stripColor(x.getItemMeta().getDisplayName());Player t=Bukkit.getPlayerExact(n);
        if(t!=null){targets.put(p.getUniqueId(),t.getUniqueId());openPlayerManager(p);}
    }

    private void openPlayerManager(Player p){
        Inventory i=gui("player",3,"&8Player Manager");
        Player t=target(p);String n=t==null?"None":t.getName();
        put(i,10,Material.PLAYER_HEAD,"&e"+n,"Selected player.");
        put(i,11,Material.BOOK,"&bStats","Health, food, XP, mode, location.");
        put(i,12,Material.CHEST,"&6Inventory","Open target inventory.");
        put(i,13,Material.GOLDEN_APPLE,"&6Heal Me","Restore target.");
        put(i,14,Material.CROSSBOW,"&cClear Inventory","Clear target inventory.");
        put(i,15,Material.ARROW,"&7Back");
        p.openInventory(i);
    }
    private void playerManager(Player p,int s){
        Player t=target(p);if(s==15){openMain(p);return;}if(t==null){openPlayers(p);return;}
        if(s==10){openPlayers(p);return;}
        if(s==11){p.sendMessage(color("&8Health: &f"+String.format("%.1f",t.getHealth())+" &7| Food: &f"+t.getFoodLevel()+" &7| XP: &f"+t.getLevel()+" &7| Mode: &f"+t.getGameMode()+" &7| World: &f"+t.getWorld().getName()));}
        else if(s==12)p.openInventory(t.getInventory());
        else if(s==13){heal(t);done(p,"Heal Me");}
        else if(s==14)confirm(p,Action.CLEAR_INV);
    }

    private void openGamemode(Player p){Inventory i=gui("gamemode",3,"&8Gamemode");put(i,10,Material.GRASS_BLOCK,"&aSurvival");put(i,11,Material.COMMAND_BLOCK,"&bCreative");put(i,12,Material.FEATHER,"&eAdventure");put(i,13,Material.ENDER_EYE,"&5Spectator");put(i,15,Material.ARROW,"&7Back");p.openInventory(i);}
    private void gamemode(Player p,int s){Player t=target(p);if(s==15){openMain(p);return;}if(t==null)return;GameMode g=s==10?GameMode.SURVIVAL:s==11?GameMode.CREATIVE:s==12?GameMode.ADVENTURE:s==13?GameMode.SPECTATOR:null;if(g!=null){t.setGameMode(g);done(p,"Gamemode");openMain(p);}}

    private void openSpeed(Player p){Inventory i=gui("speed",3,"&8Speed Controls");put(i,10,Material.SUGAR,"&fWalk Speed","Click to increase; wraps at max.");put(i,11,Material.FEATHER,"&fFly Speed","Click to increase; wraps at max.");put(i,12,Material.REDSTONE,"&7Reset Speeds");put(i,15,Material.ARROW,"&7Back");p.openInventory(i);}
    private void speed(Player p,int s){Player t=target(p);if(s==15){openMain(p);return;}if(t==null)return;float step=(float)getConfig().getDouble("settings.speed-step",0.1),max=(float)getConfig().getDouble("settings.max-speed",1.0);if(s==10)t.setWalkSpeed(next(t.getWalkSpeed(),step,max));else if(s==11)t.setFlySpeed(next(t.getFlySpeed(),step,max));else if(s==12){t.setWalkSpeed(0.2f);t.setFlySpeed(0.1f);}done(p,"Speed");openMain(p);}
    private float next(float cur,float step,float max){return cur>=max-0.001?0.1f:Math.min(max,cur+step);}

    private void openPotions(Player p){Inventory i=gui("potions",3,"&8Potion Effects");put(i,10,Material.SUGAR,"&aSpeed II","2 minutes");put(i,11,Material.IRON_CHESTPLATE,"&cResistance II","2 minutes");put(i,12,Material.FEATHER,"&bJump Boost II","2 minutes");put(i,13,Material.GLOWSTONE_DUST,"&eHaste II","2 minutes");put(i,14,Material.MILK_BUCKET,"&fClear Effects");put(i,15,Material.ARROW,"&7Back");p.openInventory(i);}
    private void potions(Player p,int s){Player t=target(p);if(s==15){openMain(p);return;}if(t==null)return;PotionEffectType type=s==10?PotionEffectType.SPEED:s==11?PotionEffectType.RESISTANCE:s==12?PotionEffectType.JUMP_BOOST:s==13?PotionEffectType.HASTE:null;if(type!=null)t.addPotionEffect(new PotionEffect(type,2400,1,true,false,true));else if(s==14)t.clearActivePotionEffects();done(p,"Potion Effects");openPotions(p);}

    private void openXp(Player p){Inventory i=gui("xp",3,"&8Experience Manager");put(i,10,Material.EXPERIENCE_BOTTLE,"&a+5 Levels");put(i,11,Material.REDSTONE,"&c-5 Levels");put(i,12,Material.BARRIER,"&fReset XP");put(i,13,Material.ENCHANTING_TABLE,"&b+1000 XP");put(i,15,Material.ARROW,"&7Back");p.openInventory(i);}
    private void xp(Player p,int s){Player t=target(p);if(s==15){openMain(p);return;}if(t==null)return;if(s==10)t.giveExpLevels(5);else if(s==11)t.giveExpLevels(-5);else if(s==12){t.setExp(0);t.setLevel(0);t.setTotalExperience(0);}else if(s==13)t.giveExp(1000);done(p,"Experience Manager");openXp(p);}

    private void openArmor(Player p){Inventory i=gui("armor",3,"&8Armor Creator");put(i,10,Material.IRON_CHESTPLATE,"&7Iron Armor");put(i,11,Material.DIAMOND_CHESTPLATE,"&bDiamond Armor");put(i,12,Material.NETHERITE_CHESTPLATE,"&5Netherite Armor");put(i,13,Material.ENCHANTED_BOOK,"&dEnchanted Netherite","Protection IV + Unbreaking III");put(i,15,Material.ARROW,"&7Back");p.openInventory(i);}
    private void armorMenu(Player p,int s){Player t=target(p);if(s==15){openMain(p);return;}if(t==null)return;Material base=s==10?Material.IRON_CHESTPLATE:s==11?Material.DIAMOND_CHESTPLATE:Material.NETHERITE_CHESTPLATE;if(s==13)base=Material.NETHERITE_CHESTPLATE;Material h=base==Material.IRON_CHESTPLATE?Material.IRON_HELMET:base==Material.DIAMOND_CHESTPLATE?Material.DIAMOND_HELMET:Material.NETHERITE_HELMET;Material l=base==Material.IRON_CHESTPLATE?Material.IRON_LEGGINGS:base==Material.DIAMOND_CHESTPLATE?Material.DIAMOND_LEGGINGS:Material.NETHERITE_LEGGINGS;Material b=base==Material.IRON_CHESTPLATE?Material.IRON_BOOTS:base==Material.DIAMOND_CHESTPLATE?Material.DIAMOND_BOOTS:Material.NETHERITE_BOOTS;ItemStack[] set={new ItemStack(b),new ItemStack(l),new ItemStack(base),new ItemStack(h)};for(ItemStack x:set)if(s==13){x.addUnsafeEnchantment(Enchantment.PROTECTION,4);x.addUnsafeEnchantment(Enchantment.UNBREAKING,3);}t.getInventory().setArmorContents(set);done(p,"Armor Creator");openMain(p);}

    private void openServer(Player p){if(!p.hasPermission("voidflame.staff.server")){msg(p,"no-permission");return;}Inventory i=gui("server",3,"&8Server Manager");put(i,10,Material.REDSTONE,"&cReload Server");put(i,11,Material.BARRIER,"&cStop Server");put(i,12,Material.WHITE_WOOL,"&fWhitelist: "+Bukkit.hasWhitelist());put(i,15,Material.ARROW,"&7Back");p.openInventory(i);}
    private void server(Player p,int s){if(s==15){openMain(p);return;}if(!p.hasPermission("voidflame.staff.server")){msg(p,"no-permission");return;}if(s==10){Bukkit.reload();done(p,"Reload Server");openMain(p);}else if(s==11)confirm(p,Action.STOP);else if(s==12){Bukkit.setWhitelist(!Bukkit.hasWhitelist());done(p,"Whitelist");openServer(p);}}

    private void openWorlds(Player p){if(!p.hasPermission("voidflame.staff.world")){msg(p,"no-permission");return;}Inventory i=gui("world",6,"&8World Manager");int s=0;for(World w:Bukkit.getWorlds()){if(s>=45)break;put(i,s,Material.GRASS_BLOCK,"&a"+w.getName(),"Left: teleport | Right: remove");s++;}put(i,49,Material.ENDER_PEARL,"&bCreate World","Creates StaffWorld_N.");put(i,50,Material.ARROW,"&7Back");p.openInventory(i);}
    private void worlds(Player p,int s,boolean rightClick){if(s==50){openMain(p);return;}if(s==49){if(!p.hasPermission("voidflame.staff.world"))return;String n="StaffWorld_"+System.currentTimeMillis()%10000;World w=Bukkit.createWorld(new WorldCreator(n));if(w!=null)p.teleport(w.getSpawnLocation());done(p,"World Created");return;}ItemStack x=p.getOpenInventory().getTopInventory().getItem(s);if(x==null||x.getItemMeta()==null)return;String n=ChatColor.stripColor(x.getItemMeta().getDisplayName());World w=Bukkit.getWorld(n);if(w==null)return;
        if(rightClick){
            if(!p.hasPermission("voidflame.staff.world")){msg(p,"no-permission");return;}
            if(w.getPlayers().stream().anyMatch(player->!player.equals(p))){msg(p,"world-has-players");return;}
            if(w.equals(p.getWorld())){msg(p,"cannot-remove-current-world");return;}
            Bukkit.unloadWorld(w,false);
            java.io.File folder=w.getWorldFolder();
            deleteFolder(folder);
            done(p,"World Removed");
            openWorlds(p);
        }else p.teleport(w.getSpawnLocation());
    }
    private void deleteFolder(java.io.File f){
        if(!f.exists())return;
        java.io.File[] children=f.listFiles();
        if(children!=null)for(java.io.File child:children)deleteFolder(child);
        f.delete();
    }

    private void openTime(Player p){Inventory i=gui("time",3,"&8Time / Weather");put(i,10,Material.SUNFLOWER,"&eDay");put(i,11,Material.CLOCK,"&6Noon");put(i,12,Material.CLOCK,"&9Night");put(i,13,Material.WATER_BUCKET,"&bClear Weather");put(i,14,Material.LIGHTNING_ROD,"&cStorm");put(i,15,Material.LODESTONE,"&5Lock/Unlock Time");put(i,16,Material.IRON_BARS,"&5Lock/Unlock Weather");put(i,17,Material.ARROW,"&7Back");p.openInventory(i);}
    private void time(Player p,int s){if(s==17){openMain(p);return;}if(s==10){lockedTime=1000;}else if(s==11){lockedTime=6000;}else if(s==12){lockedTime=13000;}else if(s==13){storm=false;}else if(s==14){storm=true;}else if(s==15){lockTime=!lockTime;}else if(s==16){lockWeather=!lockWeather;}applyTimeWeather();saveState();done(p,"Time / Weather");openTime(p);}
    private void applyTimeWeather(){for(World w:Bukkit.getWorlds()){if(lockTime)w.setTime(lockedTime);if(lockWeather){w.setStorm(storm);w.setThundering(false);}}}
    private void enforceLocks(){if(lockTime||lockWeather)applyTimeWeather();}

    private void openFlags(Player p){if(!p.hasPermission("voidflame.staff.admin")){msg(p,"no-permission");return;}Inventory i=gui("flags",3,"&8Fast Flags");put(i,10,Material.IRON_SWORD,"&cPvP: "+flags.get("pvp"));put(i,11,Material.OAK_PLANKS,"&aBlock Placement: "+flags.get("block-place"));put(i,12,Material.ZOMBIE_SPAWN_EGG,"&2Mob Spawning: "+flags.get("mob-spawning"));put(i,15,Material.ARROW,"&7Back");p.openInventory(i);}
    private void flagMenu(Player p,int s){if(!p.hasPermission("voidflame.staff.admin")){msg(p,"no-permission");return;}if(s==15){openMain(p);return;}String k=s==10?"pvp":s==11?"block-place":s==12?"mob-spawning":null;if(k!=null){flags.put(k,!flags.get(k));saveState();done(p,"Fast Flag "+k);openFlags(p);}}

    @EventHandler public void flags(EntityDamageByEntityEvent e){if(!flags.getOrDefault("pvp",true)&&e.getEntity() instanceof Player&&e.getDamager() instanceof Player)e.setCancelled(true);}
    @EventHandler public void blocks(BlockPlaceEvent e){if(!flags.getOrDefault("block-place",true)&&!e.getPlayer().hasPermission("voidflame.staff"))e.setCancelled(true);}
    @EventHandler public void mobs(CreatureSpawnEvent e){if(!flags.getOrDefault("mob-spawning",true)&&e.getSpawnReason()==CreatureSpawnEvent.SpawnReason.NATURAL)e.setCancelled(true);}

    private void confirm(Player p,Action a){if((a==Action.CLEAR_INV||a==Action.KICK_ALL||a==Action.STOP)&&!p.hasPermission("voidflame.staff.admin")){msg(p,"no-permission");return;}pending.put(p.getUniqueId(),a);confirmations.put(p.getUniqueId(),System.currentTimeMillis()+15000);Inventory i=gui("confirm",3,"&8Confirm Action");put(i,11,Material.LIME_CONCRETE,"&aConfirm");put(i,15,Material.RED_CONCRETE,"&cCancel");p.openInventory(i);}
    private void confirm(Player p,int s){if(s==15){pending.remove(p.getUniqueId());confirmations.remove(p.getUniqueId());openMain(p);return;}if(s!=11)return;Long until=confirmations.get(p.getUniqueId());Action a=pending.get(p.getUniqueId());if(until==null||until<System.currentTimeMillis()||a==null){openMain(p);return;}pending.remove(p.getUniqueId());confirmations.remove(p.getUniqueId());Player t=target(p);switch(a){case CLEAR_INV -> {if(t!=null)t.getInventory().clear();done(p,"Clear Inventory");}case KICK_ALL -> {for(Player x:Bukkit.getOnlinePlayers())if(!x.equals(p)&&(!getConfig().getBoolean("settings.kick-all-except-staff",true)||!x.hasPermission("voidflame.staff")))x.kickPlayer(color("&cRemoved by staff."));done(p,"Kick All Players");}case STOP -> {done(p,"Stop Server");Bukkit.shutdown();}case REMOVE_WORLD -> { } }if(Bukkit.isPrimaryThread()&&!a.equals(Action.STOP))openMain(p);}

    private void clearChat(Player p){if(!p.hasPermission("voidflame.staff.admin")){msg(p,"no-permission");return;}int lines=getConfig().getInt("settings.clear-chat-lines",100);for(Player x:Bukkit.getOnlinePlayers())for(int n=0;n<lines;n++)x.sendMessage(" ");done(p,"Clear Chat");openMain(p);}
    private void done(Player p,String a){getLogger().info("Staff action: "+p.getName()+" -> "+a+(target(p)==null?"":" -> "+target(p).getName()));p.sendMessage(color(getConfig().getString("messages.action","&aAction completed: &f%action%").replace("%action%",a)));}
    private void msg(Player p,String k){p.sendMessage(color(getConfig().getString("messages."+k,k)));}
    private String color(String s){return ChatColor.translateAlternateColorCodes('&',s==null?"":s);}

    static final class Holder implements InventoryHolder {final String type;Holder(String type){this.type=type;}public Inventory getInventory(){return null;}}
}
