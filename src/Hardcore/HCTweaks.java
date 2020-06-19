package Hardcore;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.UUID;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.ChatColor;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import Hardcore.commands.*;
import net.evmodder.EvLib.EvPlugin;
import net.evmodder.HorseOwners.HorseManager;

//TODO: track regions,entities, etc (fileIO with x,z,pUUID,spawnDate)
//TODO: only delete regions if there is no player that has been to that region in their current life
//TODO: spectator blacklist/whitelist
//TODO: (ChatManager) [-] -> *, no filter for self chat, toggle filtering with tag
//TODO: visible spectators
//TODO: nobody who you can spectate infinity box
//TODO: /spectate whitelist/blacklist add/remove <name> //uses add/remove instead of toggle
//NewY0rkServer1
public class HCTweaks extends EvPlugin{
	private static HCTweaks plugin; public static HCTweaks getPlugin(){return plugin;}
	String WORLD_NAME;
	final public static String DEATH_LOG_FILENAME = "deaths/log.txt";

	@Override public void onEvEnable(){
		plugin = this;
		new Extras();
		getLogger().setLevel(Level.ALL);
		WORLD_NAME = getConfig().getString("world-name", "Reliquist");
		World hardcoreWorld = getServer().getWorld(WORLD_NAME);
		hardcoreWorld.setKeepSpawnInMemory(false);
//		hardcoreWorld.setGameRule(GameRule.SPECTATORS_GENERATE_CHUNKS, true); // Is nice for spectators, but has non-vanilla side effects

		getServer().getPluginManager().registerEvents(new PlayerDeathListener(this), this);
		getServer().getPluginManager().registerEvents(new TEMP_CmdPing(this), this);
		new CommandAbout(this);
		new CommandAutoQuit(this);
		new CommandColor(this);
		new CommandDeathlog(this);
		new CommandRegionCleanup(this);
		new CommandShowScores(this, false/* TODO: enable when an event is ongoing */);
		new CommandSpectate(this);
		new CommandWhitelist(this);
		new CommandBlacklist(this);

		if(config.getBoolean("enable-compass-tracker", false))
			getServer().getPluginManager().registerEvents(new CompassManager(this), this);
		if(config.getBoolean("remember-visiting-players", false))
			getServer().getPluginManager().registerEvents(new ChunkLoadListener(this), this);
		getServer().getPluginManager().registerEvents(new NewPlayerManager(this), this);
		getServer().getPluginManager().registerEvents(new ScoreboardManager(this), this);
		getServer().getPluginManager().registerEvents(new SpectatorManager(this), this);
		getServer().getPluginManager().registerEvents(new TeleportManager(this), this);
	}
	@Override public void onEvDisable(){
		for(Player player : getServer().getOnlinePlayers()){
			if(player.getScoreboardTags().contains("unconfirmed")){
				NewPlayerManager.removeSpawnBox(player.getLocation(), player);
			}
		}
	}

	boolean deletePlayerdata(UUID uuid){
		try{
			HorseManager horsePl = (HorseManager) getServer().getPluginManager().getPlugin("HorseOwners");
			if(horsePl != null){
				ArrayList<String> horses = new ArrayList<String>();
				if(horsePl.getHorseOwners().containsKey(uuid)) horses.addAll(horsePl.getHorseOwners().get(uuid));
				for(String horseName : horses) horsePl.removeHorse(uuid, horseName, true);
			}
		}
		catch(NoClassDefFoundError ex){getLogger().severe("Failed to delete HorseOwners data");}
		getLogger().info("Deleting playerdata for: "+uuid);
		if(new File("./plugins/EvFolder/DELETED").exists()) new File("./plugins/EvFolder/DELETED").mkdir();

		copyPlayerdata(uuid, "./plugins/EvFolder/DELETED");
		return 
			new File("./" + WORLD_NAME + "/playerdata/" + uuid + ".dat").delete()
			&& new File("./" + WORLD_NAME + "/stats/" + uuid + ".json").delete()
			&& new File("./" + WORLD_NAME + "/advancements/" + uuid + ".json").delete();
	}
	public boolean copyPlayerdata(UUID uuid, String dir){
		try{
			Files.copy(new File("./" + WORLD_NAME + "/playerdata/" + uuid + ".dat").toPath(),
						new File(dir+"/playerdata_" + uuid + ".dat").toPath());
			Files.copy(new File("./" + WORLD_NAME + "/stats/" + uuid + ".json").toPath(),
						new File(dir+"/stats_" + uuid + ".json").toPath());
			Files.copy(new File("./" + WORLD_NAME + "/advancements/" + uuid + ".json").toPath(),
						new File(dir+"/advancements_" + uuid + ".json").toPath());
			return true;
		}
		catch(IOException e){return false;}
	}

	static boolean isDateStr(String filename){
		final String regex = "^[0-9]{4}-(1[0-2]|0[1-9])-(3[01]|[12][0-9]|0[1-9])$";
		Pattern pattern = Pattern.compile(regex);
		Matcher matcher = pattern.matcher(filename);
		return matcher.matches();
	}
	public static String getLastDeath(String name){
		File deathDir = new File("./plugins/EvFolder/deaths/"+name);
		if(!deathDir.exists()) return "None";
		File[] files = deathDir.listFiles();
		if(files.length == 0) return "ERROR";
		String lastDeath = "0000-00-00";
		for(File file : files) if(isDateStr(file.getName()) && file.getName().compareTo(lastDeath) > 0) lastDeath = file.getName();
		return lastDeath == "0000-00-00" ? "ERROR" : lastDeath;
	}
	public static int getNumDeaths(String name){
		File deathDir = new File("./plugins/EvFolder/deaths/"+name);
		if(!deathDir.exists()) return 0;
		int deaths = 0;
		for(File file : deathDir.listFiles()) if(isDateStr(file.getName())) ++deaths;
		return deaths;
	}
	public static long getFrequentDeathRespawnPenalty(Player spectator){
		for(String tag : spectator.getScoreboardTags()){
			if(tag.startsWith("respawn_penalty_")) return Long.parseLong(tag.substring(16));
		}
		return 0;
	}
	public static long secondsLeftUntilRespawn(Player spectator){
		int secondsSinceDeath = spectator.getStatistic(Statistic.TIME_SINCE_DEATH) / 20;
		long frequentDeathPenalty = HCTweaks.getFrequentDeathRespawnPenalty(spectator);
		long secondsLeft = HCTweaks.getPlugin().getConfig().getInt("respawn-wait", 24)*60*60 + frequentDeathPenalty - secondsSinceDeath;
		return secondsLeft;
	}
	public void resetPlayer(Player player){
		runCommand("essentials:nick "+player.getName()+" off");
		player.resetTitle();
		player.kickPlayer(ChatColor.GOLD+"Resetting playerdata...\n"+ChatColor.GRAY+"When you rejoin, you will respawn as a fresh start!");
		final UUID uuid = player.getUniqueId();
		new BukkitRunnable(){
			int attempts = 0;
			@Override public void run(){
				//Make sure they are offline
				if((getServer().getPlayer(uuid) == null && deletePlayerdata(uuid)) || ++attempts == 10) cancel();
			}
		}.runTaskTimer(this, /*delay=*/5, /*period=*/20);
	}

	public void runCommand(String command){
		getServer().dispatchCommand(getServer().getConsoleSender(), command);
	}
}