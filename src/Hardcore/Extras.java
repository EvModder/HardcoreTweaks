package Hardcore;

import java.io.File;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World.Environment;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import net.evmodder.EvLib.FileIO;
import net.evmodder.HorseOwners.HorseLibrary;

public class Extras implements Listener{
	Extras(){
		HCTweaks pl = HCTweaks.getPlugin();
		pl.getServer().getPluginManager().registerEvents(this, pl);
	}

	public static double crossDimensionalDistanceSquared(Location a, Location b){
		if(a == null || b == null) return Double.MAX_VALUE;
		if(a.getWorld().getUID().equals(b.getWorld().getUID())) return a.distanceSquared(b);
		if(a.getWorld().getEnvironment() == Environment.THE_END || b.getWorld().getEnvironment() == Environment.THE_END) return Double.MAX_VALUE;
		// By this point, we have overworld & nether
		if(a.getWorld().getEnvironment() == Environment.NETHER) return new Location(b.getWorld(), a.getX()*8, a.getY(), a.getZ()*8).distanceSquared(b);
		else return new Location(a.getWorld(), b.getX()*8, b.getY(), b.getZ()*8).distanceSquared(a);
	}

	public static void grantLocationBasedAdvancements(Player player, boolean silently){
		boolean announceAdvDefault = player.getWorld().getGameRuleDefault(GameRule.ANNOUNCE_ADVANCEMENTS);
		if(silently) player.getWorld().setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
		HCTweaks pl = HCTweaks.getPlugin();
		//pl.runCommand("minecraft:advancement grant "+player.getName()+" everything");
		String[] advancementsToGrant = new String[]{
				"minecraft:adventure/adventuring_time",
				"minecraft:end/enter_end_gateway",
				"minecraft:end/find_end_city",
				"minecraft:nether/fast_travel",
				"minecraft:nether/find_fortress",
				"minecraft:story/enter_the_nether",
				"minecraft:story/enter_the_end",
		};
		for(String advancement : advancementsToGrant){
			pl.runCommand("minecraft:advancement grant "+player.getName()+" only "+advancement);
		}
		if(silently) player.getWorld().setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, announceAdvDefault);
	}

	public static String eventStatusAug19Build(UUID uniqueId){
		File evtFile = new File("./plugins/EvFolder/events/aug19/"+uniqueId+".txt");
		if(!evtFile.exists()) return null;
		// Advancements: Foofy, Experience: Foofy, Build Contest: Xigo
		// Build Contest honorables: KingSteamRoller, KaiBe, ninjaa_momnkey, Foofy, Bella, Morphanee, Foxuwu, Elessar
		return "§6 - Aug'19 Event"+(evtFile.length() <= 10 ? " participant" : ":§c champion");
	}
	public static String eventStatusFeb20Equine(UUID uniqueId){
		File evtFile = new File("./plugins/EvFolder/events/feb20/"+uniqueId+".txt");
		if(!evtFile.exists()) return null;
		// LittlePigenz=12, Elizzar=8, Xigo=6, Foofy=5, EvModder=2, Crah=1
		final int pointsEarned = Integer.parseInt(FileIO.loadFile("events/feb20/"+uniqueId+".txt", "0"));
		if(pointsEarned > 3) return "§6 - Feb'20 Event:§c Magister Equitum";
		else return "§6 - Feb'20 Event participant";
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
	@EventHandler
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
	}
}
