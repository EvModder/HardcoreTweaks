package Hardcore;

import java.util.HashSet;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLevelChangeEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Team;
import net.evmodder.EvLib.EvUtils;

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

		/*
		Scoreboard mainBoard = pl.getServer().getScoreboardManager().getMainScoreboard();
		mainBoard.registerNewObjective("buildscore", "dummy", "§[■] Blocks Placed [■]");
		mainBoard.registerNewObjective("advancements", "dummy ", "");
		mainBoard.registerNewObjective("deaths", "deathCount", "");
		mainBoard.registerNewObjective("murderscore", "playerKillCount ", "");
		mainBoard.registerNewObjective("levels", "level", "§e- §bLevels §e-");
		mainBoard.registerNewObjective("health", "health", "Health");
		//*/
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
		HCTweaks pl = HCTweaks.getPlugin();
		pl.getServer().getScoreboardManager().getMainScoreboard().getObjective(objective).setDisplaySlot(DisplaySlot.SIDEBAR);

		if(!SIDEBAR_ACTIVE){
			SIDEBAR_ACTIVE = true;
			new BukkitRunnable(){@Override public void run(){
				pl.getServer().getScoreboardManager().getMainScoreboard().clearSlot(DisplaySlot.SIDEBAR);
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
}