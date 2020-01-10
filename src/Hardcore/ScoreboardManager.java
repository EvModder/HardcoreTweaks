package Hardcore;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLevelChangeEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import net.evmodder.EvLib.EvUtils;
import net.evmodder.HorseOwners.HorseLibrary;
import net.evmodder.HorseOwners.api.events.HorseClaimEvent;
import net.evmodder.HorseOwners.api.events.HorseDeathEvent;
import net.evmodder.HorseOwners.api.events.HorseRenameEvent;

public class ScoreboardManager implements Listener{
	final HashSet<String> included;
	final int ADV_WL_TRIGGER, LVL_WL_TRIGGER;
	final HCTweaks pl;

	public ScoreboardManager(HCTweaks plugin){
		pl = plugin;
		included = new HashSet<String>();
		included.addAll(pl.getConfig().getStringList("advancements-included"));
		ADV_WL_TRIGGER = pl.getConfig().getInt("set-whitelist-mode-when-has-x-advancements", 15);
		LVL_WL_TRIGGER = pl.getConfig().getInt("set-whitelist-mode-when-has-x-levels", 50);

		
		Scoreboard mainBoard = pl.getServer().getScoreboardManager().getMainScoreboard();
		/*mainBoard.registerNewObjective("buildscore", "dummy", "§[■] Blocks Placed [■]");
		mainBoard.registerNewObjective("advancements", "dummy ", "");
		mainBoard.registerNewObjective("deaths", "deathCount", "");
		mainBoard.registerNewObjective("murderscore", "playerKillCount ", "");
		mainBoard.registerNewObjective("levels", "level", "§e- §bLevels §e-");
		mainBoard.registerNewObjective("health", "health", "Health");
		//*/
		if(mainBoard.getObjective("horse-speed") == null){
			mainBoard.registerNewObjective("horse-speed", "dummy", "§9§m  §a Horse Speed §9§m  ");
			mainBoard.registerNewObjective("horse-health", "dummy", "§9§m  §a Horse Health §9§m  ");
			mainBoard.registerNewObjective("horse-jump", "dummy", "§9§m  §a Horse Jump §9§m  ");
			mainBoard.registerNewObjective("donkey-speed", "dummy", "§9§m  §a Donkey Speed §9§m  ");
			mainBoard.registerNewObjective("donkey-health", "dummy", "§9§m  §a Donkey Health §9§m  ");
			mainBoard.registerNewObjective("donkey-jump", "dummy", "§9§m  §a Donkey Jump §9§m  ");
			mainBoard.registerNewObjective("mule-speed", "dummy", "§9§m  §a Mule Speed §9§m  ");
			mainBoard.registerNewObjective("mule-health", "dummy", "§9§m  §a Mule Health §9§m  ");
			mainBoard.registerNewObjective("mule-jump", "dummy", "§9§m  §a Mule Jump §9§m  ");
			mainBoard.registerNewObjective("llama-speed", "dummy", "§9§m  §a Llama Speed §9§m  ");
			mainBoard.registerNewObjective("llama-health", "dummy", "§9§m  §a Llama Health §9§m  ");
			mainBoard.registerNewObjective("llama-jump", "dummy", "§9§m  §a Llama Jump §9§m  ");
			mainBoard.registerNewObjective("trader_llama-h", "dummy", "§9§m  §a TraderLlama Health §9§m  ");
			mainBoard.registerNewObjective("skeleton_horse-j", "dummy", "§9§m  §a SkeleHorse Jump §9§m  ");
		}
		//mainBoard.registerNewObjective("trader_llama-h", "dummy", "§9§m  §a TraderLlama Health §9§m  ");
		//mainBoard.registerNewObjective("skeleton_horse-j", "dummy", "§9§m  §a SkeleHorse Jump §9§m  ");
		new BukkitRunnable(){
			final Scoreboard sb = pl.getServer().getScoreboardManager().getMainScoreboard();
			final String[] horseTypes = new String[]{"horse", "donkey", "mule", "llama"};
			final String[] statTypes = new String[]{"speed", "jump", "health"};
			int typeI = 0, statI = 0;
			@Override public void run(){
				if(typeI > 3){
					if(typeI == 4){sb.getObjective("trader_llama-h").setDisplaySlot(DisplaySlot.SIDEBAR); typeI = 5;}
					else{sb.getObjective("skeleton_horse-j").setDisplaySlot(DisplaySlot.SIDEBAR); typeI = 0;}
					return;
				}
				sb.getObjective(horseTypes[typeI]+"-"+statTypes[statI]).setDisplaySlot(DisplaySlot.SIDEBAR);
				if((statI = ++statI % 3) == 0) typeI = ++typeI % 6;
			}
		}.runTaskTimer(pl, 20*5, 20*5);
	}

	boolean isMainAdvancement(Advancement adv){
		int i = adv.getKey().getKey().indexOf('/');
		return adv.getKey().getNamespace().equals(NamespacedKey.MINECRAFT) && i != -1
				&& included.contains(adv.getKey().getKey().substring(0, i));
	}

	static String getAdvancementTeamName(int numAdvancements){
		StringBuilder builder = new StringBuilder("Adv_");
		int i = 0;
		while(i<numAdvancements/26){builder.append('a'); ++i;}
		char ch = 'z'; ch -= numAdvancements%26;
		builder.append(ch); ++i;
		while(i<12){builder.append('z'); ++i;}
		return builder.toString();
	}

	static void setNumDeaths(Player player, int numDeaths){
		org.bukkit.Bukkit.getServer().getScoreboardManager().getMainScoreboard()
			.getObjective("deaths").getScore(player.getName()).setScore(numDeaths);
	}

	public static void resetScores(Player player){
		String name = player.getName();
		String name10 = name.length() > 10 ? name.substring(0, 10) : name;
		int numDeaths = player.getScoreboard().getObjective("deaths").getScore(name).getScore();
		int buildScore = player.getScoreboard().getObjective("buildscore").getScore(name10).getScore();
		player.getScoreboard().resetScores(name);
		if(name.length() > 10) player.getScoreboard().resetScores(name10);
		setNumDeaths(player, numDeaths);
		player.getScoreboard().getObjective("buildscore").getScore(name10).setScore(buildScore);//save
	}

	void addObjectiveAndTeam(Player player, int numAdvancements){
		pl.getServer().getScoreboardManager().getMainScoreboard().getObjective("advancements")
			.getScore(player.getName()).setScore(numAdvancements);

		boolean wasUpdated = false;
		String oldTeamName = getAdvancementTeamName(numAdvancements-1);
		Team oldTeam = player.getScoreboard().getTeam(oldTeamName);
		if(oldTeam != null) wasUpdated |= oldTeam.removeEntry(player.getName());
		String newTeamName = getAdvancementTeamName(numAdvancements);
		Team newTeam = player.getScoreboard().getTeam(newTeamName);
		if(newTeam == null) newTeam = player.getScoreboard().registerNewTeam(newTeamName);
		wasUpdated |= (!newTeam.hasEntry(player.getName()));
		newTeam.addEntry(player.getName());
		if(wasUpdated){//TODO: Does this actually fix the issue?
			Plugin btlp = pl.getServer().getPluginManager().getPlugin("BungeeTabListPlus");
			if(btlp != null){
				pl.getServer().getPluginManager().disablePlugin(btlp);
				pl.getServer().getPluginManager().enablePlugin(btlp);
			}
		}
	}

	@EventHandler
	public void onAchievementGet(PlayerAdvancementDoneEvent evt){
		if(!isMainAdvancement(evt.getAdvancement()) || SpectatorManager.isSpectatorFavorYes(evt.getPlayer())) return;
		int advancements = EvUtils.getVanillaAdvancements(evt.getPlayer(), included).size();
		pl.getLogger().info(evt.getPlayer()+" now has "+advancements+" advancements");
		if(advancements == ADV_WL_TRIGGER &&
				!evt.getPlayer().getScoreboardTags().contains("blacklist_mode")){
			evt.getPlayer().addScoreboardTag("whitelist_mode");
		}
		addObjectiveAndTeam(evt.getPlayer(), advancements);
	}

	private static boolean SIDEBAR_ACTIVE = false;
	public static void showOnSidebar(String objective, int seconds){
		final HCTweaks pl = HCTweaks.getPlugin();
		final Scoreboard mainBoard = pl.getServer().getScoreboardManager().getMainScoreboard();
		mainBoard.getObjective(objective).setDisplaySlot(DisplaySlot.SIDEBAR);

		if(!SIDEBAR_ACTIVE){
			SIDEBAR_ACTIVE = true;
			new BukkitRunnable(){@Override public void run(){
				mainBoard.clearSlot(DisplaySlot.SIDEBAR);
				SIDEBAR_ACTIVE = false;
			}}.runTaskLater(pl, 20*seconds);
		}
	}

	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent evt){
		//showOnSidebar("levels", 5);//TODO: remove
		UUID uuid = evt.getPlayer().getUniqueId();
		new BukkitRunnable(){@Override public void run(){
			Player player = pl.getServer().getPlayer(uuid);
			if(player != null && !SpectatorManager.isSpectatorFavorYes(player)){
				int advancements = EvUtils.getVanillaAdvancements(player, included).size();
				pl.getLogger().info(player.getName()+" has "+advancements+" advancements");
				addObjectiveAndTeam(player, advancements);
			}
		}}.runTaskLater(pl, 20*5); //5s
	}

	@EventHandler
	public void onLevelUp(PlayerLevelChangeEvent evt){
		//showOnSidebar("levels", 30);//TODO: remove
		if(evt.getNewLevel() == LVL_WL_TRIGGER && !evt.getPlayer().getScoreboardTags().contains("blacklist_mode")){
			evt.getPlayer().addScoreboardTag("whitelist_mode");
		}
	}

	// January 2020 event!
	private void updateHorseScoreboard(AbstractHorse horse, String name){
		Scoreboard sb = pl.getServer().getScoreboardManager().getMainScoreboard();
		switch(horse.getType()){
			case HORSE:
			case DONKEY:
			case MULE:
			case LLAMA:
				String horseType = horse.getType().name().toLowerCase();
				sb.getObjective(horseType+"-speed").getScore(name)
							.setScore((int)(100*HorseLibrary.getNormalSpeed(horse)));
				sb.getObjective(horseType+"-jump").getScore(name)
							.setScore((int)(100*HorseLibrary.getNormalJump(horse)));
				sb.getObjective(horseType+"-health").getScore(name)
							.setScore(HorseLibrary.getNormalMaxHealth(horse));
				return;
			case TRADER_LLAMA:
				sb.getObjective("trader_llama-h").getScore(name)
							.setScore(HorseLibrary.getNormalMaxHealth(horse));
				return;
			case SKELETON_HORSE:
				sb.getObjective("skeleton_horse-j").getScore(name)
							.setScore((int)(100*HorseLibrary.getNormalJump(horse)));
				return;
			default:
		}
	}
	private void renameHorseScoreboard(String oldName, String newName){
		pl.getLogger().info("Updating scoreboard of '"+oldName+"' to '"+newName+"'");
		Scoreboard sb = pl.getServer().getScoreboardManager().getMainScoreboard();
		final HashMap<Objective, Integer> oldScores = new HashMap<Objective, Integer>();
		final HashMap<Objective, Integer> horseScores = new HashMap<Objective, Integer>();
		for(Objective objective : sb.getObjectives()){
			Score score = objective.getScore(oldName);
			if(!score.isScoreSet()) continue;
			if(objective.getName().startsWith("horse-") ||
				objective.getName().startsWith("donkey-") ||
				objective.getName().startsWith("mule-") ||
				objective.getName().startsWith("llama-") ||
				objective.getName().startsWith("trader_llama-") ||
				objective.getName().startsWith("skeleton_horse-")
			) horseScores.put(objective, score.getScore());
			else oldScores.put(objective, score.getScore());
		}
		sb.resetScores(oldName);
		for(Entry<Objective, Integer> entry : oldScores.entrySet()){
			entry.getKey().getScore(oldName).setScore(entry.getValue());
		}
		if(newName != null)
		for(Entry<Objective, Integer> entry : horseScores.entrySet()){
			entry.getKey().getScore(newName).setScore(entry.getValue());
		}
	}

	@EventHandler public void onHorseClaim(HorseClaimEvent evt){
		if(evt.getEntity() instanceof AbstractHorse){
			updateHorseScoreboard((AbstractHorse) evt.getEntity(), evt.getClaimName());
		}
	}
	@EventHandler public void onHorseRename(HorseRenameEvent evt){
		renameHorseScoreboard(evt.getOldFullName(), evt.getNewFullName());
	}
	@EventHandler public void onHorseDeath(HorseDeathEvent evt){
		renameHorseScoreboard(evt.getEntity().getCustomName(), null);
	}
}