package Hardcore;

import java.io.File;
import java.util.ArrayList;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.scheduler.BukkitRunnable;
import net.evmodder.EvLib.FileIO;
import net.evmodder.HorseOwners.HorseManager;

public class Extras implements Listener{
	Extras(){
		HCTweaks pl = HCTweaks.getPlugin();
		pl.getServer().getPluginManager().registerEvents(this, pl);
	}

	public static void grantLocationBasedAdvancements(Player player, boolean silently){
		HCTweaks pl = HCTweaks.getPlugin();
		if(!silently){
			//pl.runCommand("minecraft:advancement grant "+player.getName()+" everything");
			String[] advancementsToGrant = new String[]{
					/*"minecraft:*/"adventure/adventuring_time",
					/*"minecraft:*/"end/enter_end_gateway",
					/*"minecraft:*/"end/find_end_city",
					/*"minecraft:*/"nether/fast_travel",
					/*"minecraft:*/"nether/find_fortress",
					/*"minecraft:*/"nether/find_bastion",
					/*"minecraft:*/"nether/explore_nether",
					/*"minecraft:*/"nether/create_beacon", // Within a 20×20×14 cuboid centered on a beacon block when becomes powered
					/*"minecraft:*/"nether/create_full_beacon", // Within a 20×20×14 cuboid centered on a beacon block when becomes full power
					/*"minecraft:*/"story/enter_the_nether",
					/*"minecraft:*/"story/enter_the_end",
			};
			for(String advancement : advancementsToGrant){
				try{
					NamespacedKey key = NamespacedKey.minecraft(advancement);
					AdvancementProgress progress = player.getAdvancementProgress(pl.getServer().getAdvancement(key));
					for(String criteria : progress.getRemainingCriteria()) progress.awardCriteria(criteria);
				}
				catch(IllegalArgumentException ex){/*adv no longer exists or not yet added*/}
				// Alternative way (downside: outputs to console)
//				pl.runCommand("minecraft:advancement grant "+player.getName()+" only "+advancement);
			}
		}
		else{
			// TODO: prevent advancement poppup on player screen
			/*Listener hideAdvancementPacket = new Listener(){
				@EventHandler
				public void stopAdvancement(PlayerPlayOutPacketEvent evt){
					
				}
			};
			pl.getServer().getPluginManager().registerEvents(hideAdvancementPacket, pl);*/
			for(final World w : player.getServer().getWorlds()){
				if(w.getGameRuleDefault(GameRule.ANNOUNCE_ADVANCEMENTS)){
					w.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
					new BukkitRunnable(){@Override public void run(){
						w.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, true);
//						HandlerList.unregisterAll(hideAdvancementPacket);
					}}.runTaskLater(pl, 10);
				}
			}
			new BukkitRunnable(){@Override public void run(){grantLocationBasedAdvancements(player, false);}}.runTaskLater(pl, 5);
		}
	}
	public static void grantItemBasedAdvancements(Player player, boolean silently){
		HCTweaks pl = HCTweaks.getPlugin();
		if(!silently){
			String[] advancementsToGrant = new String[]{
					/*"minecraft:*/"story/root",
					/*"minecraft:*/"story/mine_stone",
					/*"minecraft:*/"story/upgrade_tools",
					/*"minecraft:*/"story/smelt_iron",
					/*"minecraft:*/"story/obtain_armor",
					/*"minecraft:*/"story/lava_bucket",
					/*"minecraft:*/"story/iron_tools",
					/*"minecraft:*/"story/form_obsidian",
					/*"minecraft:*/"story/mine_diamond",
					/*"minecraft:*/"story/shiny_gear",
					/*"minecraft:*/"nether/obtain_ancient_debris",
					/*"minecraft:*/"nether/obtain_crying_obsidian",
					/*"minecraft:*/"nether/use_lodestone",
					/*"minecraft:*/"nether/get_wither_skull",
					/*"minecraft:*/"nether/obtain_blaze_rod",
					/*"minecraft:*/"end/dragon_egg",
					/*"minecraft:*/"end/dragon_breath",
					/*"minecraft:*/"end/elytra",
					/*"minecraft:*/"husbandry/tactical_fishing",
					/*"minecraft:*/"husbandry/break_diamond_hoe",
			};
			for(String advancement : advancementsToGrant){
				try{
					NamespacedKey key = NamespacedKey.minecraft(advancement);
					AdvancementProgress progress = player.getAdvancementProgress(pl.getServer().getAdvancement(key));
					for(String criteria : progress.getRemainingCriteria()) progress.awardCriteria(criteria);
				}
				catch(IllegalArgumentException ex){/*adv no longer exists or not yet added*/}
			}
		}
		else{
			for(final World w : player.getServer().getWorlds()){
				if(w.getGameRuleDefault(GameRule.ANNOUNCE_ADVANCEMENTS)){
					w.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
					new BukkitRunnable(){@Override public void run(){w.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, true);}}.runTaskLater(pl, 10);
				}
			}
			new BukkitRunnable(){@Override public void run(){grantItemBasedAdvancements(player, false);}}.runTaskLater(pl, 5);
		}
	}

	public static String eventStatusAug19Build(UUID uniqueId){
		File evtFile = new File("./plugins/EvFolder/events/aug19/"+uniqueId+".txt");
		if(!evtFile.exists()) return null;
		// Advancements: Foofy, Experience: Foofy, Build Contest: Xigo
		// Build Contest honorables: KingSteamRoller, KaiBe, ninjaa_momnkey, Foofy, Bella, Morphanee, Foxuwu, Elessar
		return "§6 - Aug'19 Event"+(evtFile.length() <= 10 ? " participant" : ":§c Champion");
	}
	public static String eventStatusFeb20Equine(UUID uniqueId){
		File evtFile = new File("./plugins/EvFolder/events/feb20/"+uniqueId+".txt");
		if(!evtFile.exists()) return null;
		// LittlePigenz=12, Elizzar=8, Xigo=6, Foofy=5, EvModder=2, Crah=1
		final int pointsEarned = Integer.parseInt(FileIO.loadFile("events/feb20/"+uniqueId+".txt", "0"));
		if(pointsEarned > 3) return "§6 - Feb'20 Event:§c Magister Equitum";
		else return "§6 - Feb'20 Event participant";
	}

	public static void freeOwnedHorses(UUID playerUUID, boolean removeCompletely){
		HCTweaks pl = HCTweaks.getPlugin();
		try{
			HorseManager horsePl = (HorseManager) pl.getServer().getPluginManager().getPlugin("HorseOwners");
			if(horsePl != null){
				ArrayList<String> horses = new ArrayList<String>();
				if(horsePl.getHorseOwners().containsKey(playerUUID)) horses.addAll(horsePl.getHorseOwners().get(playerUUID));
				for(String horseName : horses) horsePl.removeHorse(playerUUID, horseName, removeCompletely);
			}
		}
		catch(NoClassDefFoundError ex){pl.getLogger().severe("Failed to delete HorseOwners data");}
	}

/*	public static boolean setPermissionX(Player player, String permission, boolean value){
		if(player.hasPermission(permission) == value) return false;
		HCTweaks.getPlugin().runCommand("perms player setperm "+player.getName()+" "+permission+" "+(""+value).toLowerCase());
//		final PermissionsPlugin permsPlugin = (PermissionsPlugin)
//				pl.getServer().getPluginManager().getPlugin("PermissionsBukkit");
//		if(permsPlugin == null) return false;
//		PermissionInfo playerInfo = permsPlugin.getPlayerInfo(player.getUniqueId());
//		String uuid = player.getUniqueId().toString();
//		if(playerInfo == null){
//			permsPlugin.getConfig().set("users."+uuid, new YamlConfiguration());
//			permsPlugin.getConfig().set("users."+uuid+".name", player.getName());
//			permsPlugin.getConfig().set("users."+uuid+".permissions", new YamlConfiguration());
//		}
//		permsPlugin.getConfig().set("users."+uuid+".permissions."+permission, value);
//		java.lang.reflect.Method methodRegisterPlayer;
//		try{methodRegisterPlayer = permsPlugin.getClass().getDeclaredMethod("registerPlayer", Player.class);}
//		catch(SecurityException | NoSuchMethodException ex){
//			pl.getLogger().warning("Failed to access registerPlayer(): " + ex.getMessage());
//			return false;
//		}
//		methodRegisterPlayer.setAccessible(true);
//		try{methodRegisterPlayer.invoke(permsPlugin, player);}
//		catch(IllegalAccessException | IllegalArgumentException | InvocationTargetException ex){
//			pl.getLogger().warning("Failed to invoke registerPlayer(): " + ex.getMessage());
//			return false;
//		}
		return true;
	}*/

	@EventHandler
	public void onPreCommand(PlayerCommandPreprocessEvent evt){
		if(evt.getMessage().charAt(0) != '/' || evt.isCancelled()) return;
		String message = evt.getMessage().toLowerCase();
		String command = message.split(" ")[0];

		if(command.contains("suicide") && !evt.getPlayer().isOp() && evt.getPlayer().getGameMode() == GameMode.SPECTATOR){
			evt.setCancelled(true);
		}
	}

	//TODO: re-enable if needed
	/*@EventHandler
	public void onChunkLoad(ChunkLoadEvent evt){
		if(evt.isNewChunk() || evt.isAsynchronous()) return;
		for(Entity e : evt.getChunk().getEntities()){
			if(e instanceof AbstractHorse){
				AbstractHorse horse = (AbstractHorse)e;
				double speed = HorseLibrary.getNormalSpeed(horse), jump = HorseLibrary.getNormalJump(horse), health = HorseLibrary.getNormalMaxHealth(horse);
				if(speed > 16 || jump > 6 || health > 35){
					horse.remove();
					HCTweaks.getPlugin().getLogger().warning("Deleted illegit horse: s,j,h="+speed+","+jump+","+health);
				}
			}
		}
	}*/
}
